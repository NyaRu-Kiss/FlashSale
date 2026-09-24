package com.flashsale.coupon;
import com.flashsale.common.security.Principal;import com.flashsale.common.trace.TraceContext;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.util.UUID;
@Service class CouponClaimService {
 private final JdbcTemplate jdbc;
 private final RedisCouponClaimInventory redis;
 CouponClaimService(JdbcTemplate j,RedisCouponClaimInventory r){jdbc=j;redis=r;}
 @Transactional long claim(Principal p,long templateId,String key){
  if(p==null||p.role()!=com.flashsale.common.security.Role.CUSTOMER)throw new IllegalArgumentException("FORBIDDEN");
  if(key==null||key.isBlank())throw new IllegalArgumentException("VALIDATION_ERROR");
  try{jdbc.update("insert into coupon_claim_idempotency(user_id,coupon_template_id,idempotency_key,request_fingerprint) values(?,?,?,?)",p.userId(),templateId,key,Integer.toHexString(key.hashCode()));}
  catch(Exception e){Long prior=jdbc.query("select user_coupon_id from coupon_claim_idempotency where user_id=? and coupon_template_id=? and idempotency_key=?",r->r.next()?r.getObject(1,Long.class):null,p.userId(),templateId,key);if(prior!=null)return prior;throw new IllegalArgumentException("REQUEST_IN_PROGRESS");}
  ClaimConfig config=jdbc.queryForObject("select issue_limit,issued_count,claim_limit_per_user,status::text,now() between claim_starts_at and claim_ends_at from coupon_template where id=?",(r,n)->new ClaimConfig(r.getInt(1),r.getInt(2),r.getInt(3),r.getString(4),r.getBoolean(5)),templateId);
  if(config==null||!config.claimable())throw new IllegalArgumentException("COUPON_NOT_AVAILABLE");
  Integer claimed=jdbc.queryForObject("select coalesce(claimed_count,0) from coupon_user_claim_counter where coupon_template_id=? and user_id=?",Integer.class,templateId,p.userId());
  RedisCouponClaimInventory.Reservation reservation=redis.reserve(templateId,p.userId(),key,config.issueLimit(),config.issuedCount(),config.claimLimit(),claimed==null?0:claimed);
  if(!reservation.accepted())throw new IllegalArgumentException(reservation.code());
  boolean committed=false;
  try {
   if(jdbc.update("update coupon_template set issued_count=issued_count+1,version=version+1 where id=? and issued_count<issue_limit",templateId)!=1)throw new IllegalArgumentException("COUPON_NOT_AVAILABLE");
   if(jdbc.update("insert into coupon_user_claim_counter(coupon_template_id,user_id,claimed_count) values(?,?,1) on conflict(coupon_template_id,user_id) do update set claimed_count=coupon_user_claim_counter.claimed_count+1 where coupon_user_claim_counter.claimed_count<?",templateId,p.userId(),config.claimLimit())!=1)throw new IllegalArgumentException("COUPON_CLAIM_LIMIT_EXCEEDED");
   Long coupon=jdbc.queryForObject("insert into user_coupon(coupon_template_id,user_id) values(?,?) returning id",Long.class,templateId,p.userId());
   jdbc.update("update coupon_claim_idempotency set status='SUCCEEDED',user_coupon_id=?,response_code='SUCCESS',completed_at=now() where user_id=? and coupon_template_id=? and idempotency_key=?",coupon,p.userId(),templateId,key);
   jdbc.update("insert into coupon_outbox(event_id,event_type,idempotency_key,aggregate_type,aggregate_id,payload,trace_id) values(?,?,?,?,?,?,?)",UUID.randomUUID(),"COUPON_CLAIMED","COUPON_CLAIM_"+key,"USER_COUPON",coupon,"{\"template_id\":"+templateId+",\"user_id\":"+p.userId()+"}",TraceContext.getOrCreate());
   committed=true;return coupon;
  } finally { if(!committed&&!redis.compensate(templateId,p.userId(),key))throw new IllegalStateException("COUPON_REDIS_COMPENSATION_FAILED"); }
 }
 private record ClaimConfig(int issueLimit,int issuedCount,int claimLimit,String status,boolean inWindow){boolean claimable(){return "ACTIVE".equals(status)&&inWindow;}}
 java.util.List<UserCouponView> mine(Principal p,String status){if(p==null||p.role()!=com.flashsale.common.security.Role.CUSTOMER)throw new IllegalArgumentException("FORBIDDEN");if(status!=null&&!status.isBlank()&&!java.util.Set.of("AVAILABLE","RESERVED","CONSUMED","EXPIRED").contains(status))throw new IllegalArgumentException("VALIDATION_ERROR");return jdbc.query("select uc.id,uc.coupon_template_id,uc.status,uc.claimed_at,ct.threshold_minor,ct.discount_minor,ct.use_starts_at,ct.use_ends_at from user_coupon uc join coupon_template ct on ct.id=uc.coupon_template_id where uc.user_id=? and (? is null or uc.status=?) order by uc.id desc",(r,n)->new UserCouponView(r.getLong(1),r.getLong(2),r.getString(3),r.getObject(4,java.time.OffsetDateTime.class),r.getLong(5),r.getLong(6),r.getObject(7,java.time.OffsetDateTime.class),r.getObject(8,java.time.OffsetDateTime.class)),p.userId(),status==null||status.isBlank()?null:status,status==null||status.isBlank()?null:status);}
 record UserCouponView(long id,long templateId,String status,java.time.OffsetDateTime claimedAt,long thresholdMinor,long discountMinor,java.time.OffsetDateTime useStartsAt,java.time.OffsetDateTime useEndsAt){}
}

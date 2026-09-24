package com.flashsale.coupon;

import com.fasterxml.jackson.core.type.TypeReference;
import com.flashsale.common.cache.CacheAsideReader;
import com.flashsale.common.cache.CacheInvalidator;
import com.flashsale.common.cache.CacheValue;
import com.flashsale.common.security.Principal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
class CouponTemplateService {
    private static final String CLAIMABLE_LIST_KEY="cache:coupon-template:claimable:list";
    private final CouponTemplateRepository repo; private final CacheAsideReader cache; private final CacheInvalidator invalidator; private final CouponWriteRepository writes;
    CouponTemplateService(CouponTemplateRepository r,CacheAsideReader cache,CacheInvalidator invalidator,CouponWriteRepository writes){repo=r;this.cache=cache;this.invalidator=invalidator;this.writes=writes;}
    private void op(Principal p){if(p==null||!p.canManageBusiness())throw new IllegalArgumentException("FORBIDDEN");}
    @Transactional CouponTemplate create(Principal p,CouponTemplate x){op(p);if(x.discountMinor()<=0||x.thresholdMinor()<0||x.discountMinor()>x.thresholdMinor()&&x.thresholdMinor()>0||x.issueLimit()<=0||x.claimLimitPerUser()<=0||x.claimStartsAt()==null||!x.claimStartsAt().isBefore(x.claimEndsAt())||!x.useStartsAt().isBefore(x.useEndsAt()))throw new IllegalArgumentException("VALIDATION_ERROR");invalidator.invalidate(CLAIMABLE_LIST_KEY);CouponTemplate created=repo.create(new CouponTemplate(0,x.name(),x.thresholdMinor(),x.discountMinor(),x.issueLimit(),x.claimLimitPerUser(),x.claimStartsAt(),x.claimEndsAt(),x.useStartsAt(),x.useEndsAt(),"DRAFT",p.userId()));writes.audit(p.userId(),created.id(),"CREATE",null,created);writes.outbox(created.id());return created;}
    @Transactional CouponTemplate update(Principal p,long id,CouponTemplate x){op(p);CouponTemplate old=repo.get(id);if(old==null)throw new IllegalArgumentException("COUPON_TEMPLATE_NOT_FOUND");invalidator.invalidate(CLAIMABLE_LIST_KEY);CouponTemplate y=repo.update(id,new CouponTemplate(id,x.name(),x.thresholdMinor(),x.discountMinor(),x.issueLimit(),x.claimLimitPerUser(),x.claimStartsAt(),x.claimEndsAt(),x.useStartsAt(),x.useEndsAt(),old.status(),p.userId()));if(y==null)throw new IllegalArgumentException("COUPON_TEMPLATE_ALREADY_ISSUED");writes.audit(p.userId(),id,"UPDATE",old,y);writes.outbox(id);return y;}
    @Transactional CouponTemplate status(Principal p,long id,String s){op(p);if(!s.equals("PAUSED")&&!s.equals("ACTIVE"))throw new IllegalArgumentException("VALIDATION_ERROR");CouponTemplate old=repo.get(id);if(old==null)throw new IllegalArgumentException("COUPON_TEMPLATE_NOT_FOUND");invalidator.invalidate(CLAIMABLE_LIST_KEY);CouponTemplate y=repo.status(id,s,p.userId());if(y==null)throw new IllegalArgumentException("COUPON_TEMPLATE_NOT_FOUND");writes.audit(p.userId(),id,"PAUSED".equals(s)?"PAUSE":"RESUME",old,y);writes.outbox(id);return y;}
    CouponTemplate get(long id){CouponTemplate y=repo.get(id);if(y==null)throw new IllegalArgumentException("COUPON_TEMPLATE_NOT_FOUND");return y;}
    List<CouponTemplate> list(boolean a){return repo.list(a);}
    List<CouponTemplate> claimable(){return cache.read(CLAIMABLE_LIST_KEY,new TypeReference<CacheValue<List<CouponTemplate>>>() {},()->CacheValue.value(repo.list(true))).value();}
}

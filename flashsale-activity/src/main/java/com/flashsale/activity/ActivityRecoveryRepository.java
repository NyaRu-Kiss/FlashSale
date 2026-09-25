package com.flashsale.activity;
import java.sql.ResultSet; import java.sql.SQLException; import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository;
@Repository class ActivityRecoveryRepository {
 private final JdbcTemplate jdbc; ActivityRecoveryRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 ActivityRecoveryJob pendingOrCreate(long activityId,long actor){ActivityRecoveryJob x=active(activityId);if(x!=null)return x;return jdbc.queryForObject("insert into activity_recovery_job(activity_id,requested_by) values (?,?) returning id,activity_id,requested_by,recovery_barrier_sequence,status,last_error,requested_at,completed_at",(r,n)->map(r),activityId,actor);}
 ActivityRecoveryJob latest(long activityId){return jdbc.query("select id,activity_id,requested_by,recovery_barrier_sequence,status,last_error,requested_at,completed_at from activity_recovery_job where activity_id=? order by id desc limit 1",(r,n)->map(r),activityId).stream().findFirst().orElse(null);}
 ActivityRecoveryJob byId(long id){return jdbc.query("select id,activity_id,requested_by,recovery_barrier_sequence,status,last_error,requested_at,completed_at from activity_recovery_job where id=?",(r,n)->map(r),id).stream().findFirst().orElse(null);}
 List<Long> pendingIds(){return jdbc.queryForList("select id from activity_recovery_job where status='PENDING' order by id",Long.class);}
 ActivityRecoveryJob claim(long id,long barrier){return jdbc.query("update activity_recovery_job set status='RUNNING',recovery_barrier_sequence=?,started_at=now(),last_error=null where id=? and status='PENDING' returning id,activity_id,requested_by,recovery_barrier_sequence,status,last_error,requested_at,completed_at",(r,n)->map(r),barrier,id).stream().findFirst().orElse(null);}
 void succeeded(long id, long activityId, String traceId){
  jdbc.update("update activity_recovery_job set status='SUCCEEDED',completed_at=now() where id=? and status='RUNNING'",id);
  jdbc.update("""
   insert into activity_outbox(event_id,event_type,idempotency_key,aggregate_type,aggregate_id,payload,trace_id)
   values (?, 'ACTIVITY_RESUMED', ?, 'ACTIVITY', ?, ?::jsonb, ?)
   on conflict (idempotency_key) do nothing
   """, java.util.UUID.randomUUID(), "ACTIVITY:RESUMED:" + activityId + ":" + id,
   Long.toString(activityId), "{\"activity_id\":" + activityId + ",\"recovery_job_id\":" + id
       + ",\"trace_id\":\"" + traceId + "\"}", traceId);
 }
 void failed(long id,String error){jdbc.update("update activity_recovery_job set status='FAILED',last_error=?,completed_at=now() where id=? and status='RUNNING'",error,id);}
 private ActivityRecoveryJob active(long activityId){return jdbc.query("select id,activity_id,requested_by,recovery_barrier_sequence,status,last_error,requested_at,completed_at from activity_recovery_job where activity_id=? and status in ('PENDING','RUNNING') order by id desc limit 1",(r,n)->map(r),activityId).stream().findFirst().orElse(null);}
 private ActivityRecoveryJob map(ResultSet r)throws SQLException{return new ActivityRecoveryJob(r.getLong(1),r.getLong(2),r.getLong(3),r.getObject(4,Long.class),ActivityRecoveryStatus.valueOf(r.getString(5)),r.getString(6),r.getObject(7,java.time.OffsetDateTime.class),r.getObject(8,java.time.OffsetDateTime.class));}
}

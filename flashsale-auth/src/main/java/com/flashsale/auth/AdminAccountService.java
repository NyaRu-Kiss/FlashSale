package com.flashsale.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import com.flashsale.common.trace.TraceContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
class AdminAccountService {
    private final AuthUserRepository users;
    private final PasswordEncoder encoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    AdminAccountService(AuthUserRepository users, PasswordEncoder encoder, org.springframework.jdbc.core.JdbcTemplate jdbc, ObjectMapper mapper) { this.users=users; this.encoder=encoder; this.jdbc=jdbc; this.mapper=mapper; }
    @Transactional public AuthUser create(Principal actor, String username, String password, Role role) {
        requireAdmin(actor); validate(username,password,role);
        try { AuthUser u=users.create(username,encoder.encode(password),role); audit(actor,u.id(),"CREATE_ACCOUNT",null,u); return u; }
        catch(DataIntegrityViolationException e){ throw new IllegalArgumentException("USERNAME_ALREADY_EXISTS",e); }
    }
    public List<AuthUser> list(Principal actor,int page,int size){ requireAdmin(actor); if(page<1||size<1||size>100) throw new IllegalArgumentException("VALIDATION_ERROR"); return users.findAll((page-1)*size,size); }
    public long count(Principal actor){ requireAdmin(actor); return users.count(); }
    public List<AccountAudit> audits(Principal actor, int page, int size) {
        requireAdmin(actor);
        if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("VALIDATION_ERROR");
        return jdbc.query("select id,operator_id,target_id,action,trace_id,created_at from operator_audit_log "
                        + "where target_type = 'USER_ACCOUNT' order by id desc limit ? offset ?",
                (rs, row) -> new AccountAudit(rs.getLong("id"), rs.getLong("operator_id"), rs.getLong("target_id"),
                        rs.getString("action"), rs.getString("trace_id"), rs.getObject("created_at", java.time.OffsetDateTime.class)),
                size, (page - 1) * size);
    }
    public long auditCount(Principal actor) {
        requireAdmin(actor);
        Long count = jdbc.queryForObject("select count(*) from operator_audit_log where target_type = 'USER_ACCOUNT'", Long.class);
        return count == null ? 0 : count;
    }
    public AuthUser get(Principal actor,long id){ requireAdmin(actor); return users.findById(id).orElseThrow(()->new IllegalArgumentException("RESOURCE_NOT_FOUND")); }
    @Transactional public AuthUser changeRole(Principal actor,long id,Role role){ requireAdmin(actor); if(actor.userId()==id) throw new IllegalArgumentException("SELF_ROLE_CHANGE_FORBIDDEN"); AuthUser before=get(actor,id); if(before.role()==Role.ADMIN&&role!=Role.ADMIN&&activeAdmins()<=1) throw new IllegalArgumentException("LAST_ACTIVE_ADMIN_PROTECTED"); AuthUser after=users.updateRole(id,role); audit(actor,id,"UPDATE_ROLE",before,after); return after; }
    @Transactional public AuthUser setStatus(Principal actor,long id,String status){ requireAdmin(actor); if(!"ACTIVE".equals(status)&&!"DISABLED".equals(status)) throw new IllegalArgumentException("VALIDATION_ERROR"); if(actor.userId()==id&&"DISABLED".equals(status)) throw new IllegalArgumentException("SELF_DISABLE_FORBIDDEN"); AuthUser before=get(actor,id); if(before.role()==Role.ADMIN&&before.active()&&"DISABLED".equals(status)&&activeAdmins()<=1) throw new IllegalArgumentException("LAST_ACTIVE_ADMIN_PROTECTED"); AuthUser after=users.updateStatus(id,status); audit(actor,id,"ACTIVE".equals(status)?"ENABLE_ACCOUNT":"DISABLE_ACCOUNT",before,after); return after; }
    private long activeAdmins(){ Long n=jdbc.queryForObject("select count(*) from app_user where role='ADMIN' and status='ACTIVE'",Long.class); return n==null?0:n; }
    private void requireAdmin(Principal p){ if(p==null||!p.canManageAccounts()) throw new IllegalArgumentException("FORBIDDEN"); }
    private void validate(String u,String p,Role r){ if(u==null||u.isBlank()||u.length()>64||p==null||p.length()<8||r==null) throw new IllegalArgumentException("VALIDATION_ERROR"); }
    private void audit(Principal a,long id,String action,AuthUser before,AuthUser after){
        String traceId = TraceContext.getOrCreate();
        jdbc.update("insert into operator_audit_log(operator_id,target_type,target_id,action,before_snapshot,after_snapshot,trace_id,request_source) values (?, 'USER_ACCOUNT', ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)",
                a.userId(),id,action,snapshot(before),snapshot(after),traceId,"admin:" + a.userId());
    }
    private String snapshot(AuthUser u){
        if (u == null) return null;
        try {
            var node = mapper.createObjectNode();
            node.put("id", u.id());
            node.put("username", u.username());
            node.put("role", u.role().name());
            node.put("status", u.status());
            return mapper.writeValueAsString(node);
        }
        catch (JsonProcessingException e) { throw new IllegalStateException("AUDIT_SERIALIZATION_FAILED", e); }
    }
    record AccountAudit(long id, long operatorId, long targetId, String action, String traceId, java.time.OffsetDateTime createdAt) {}
}

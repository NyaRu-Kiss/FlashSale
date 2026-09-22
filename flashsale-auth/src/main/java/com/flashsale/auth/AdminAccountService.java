package com.flashsale.auth;

import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
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
    AdminAccountService(AuthUserRepository users, PasswordEncoder encoder, org.springframework.jdbc.core.JdbcTemplate jdbc) { this.users=users; this.encoder=encoder; this.jdbc=jdbc; }
    @Transactional public AuthUser create(Principal actor, String username, String password, Role role) {
        requireAdmin(actor); validate(username,password,role);
        try { AuthUser u=users.create(username,encoder.encode(password),role); audit(actor,u.id(),"CREATE_ACCOUNT",null,u); return u; }
        catch(DataIntegrityViolationException e){ throw new IllegalArgumentException("USERNAME_ALREADY_EXISTS",e); }
    }
    public List<AuthUser> list(Principal actor,int page,int size){ requireAdmin(actor); if(page<1||size<1||size>100) throw new IllegalArgumentException("VALIDATION_ERROR"); return users.findAll((page-1)*size,size); }
    public long count(Principal actor){ requireAdmin(actor); return users.count(); }
    public AuthUser get(Principal actor,long id){ requireAdmin(actor); return users.findById(id).orElseThrow(()->new IllegalArgumentException("RESOURCE_NOT_FOUND")); }
    @Transactional public AuthUser changeRole(Principal actor,long id,Role role){ requireAdmin(actor); if(actor.userId()==id) throw new IllegalArgumentException("SELF_ROLE_CHANGE_FORBIDDEN"); AuthUser before=get(actor,id); if(before.role()==Role.ADMIN&&role!=Role.ADMIN&&activeAdmins()<=1) throw new IllegalArgumentException("LAST_ACTIVE_ADMIN_PROTECTED"); AuthUser after=users.updateRole(id,role); audit(actor,id,"UPDATE_ROLE",before,after); return after; }
    @Transactional public AuthUser setStatus(Principal actor,long id,String status){ requireAdmin(actor); if(!"ACTIVE".equals(status)&&!"DISABLED".equals(status)) throw new IllegalArgumentException("VALIDATION_ERROR"); if(actor.userId()==id&&"DISABLED".equals(status)) throw new IllegalArgumentException("SELF_DISABLE_FORBIDDEN"); AuthUser before=get(actor,id); if(before.role()==Role.ADMIN&&before.active()&&"DISABLED".equals(status)&&activeAdmins()<=1) throw new IllegalArgumentException("LAST_ACTIVE_ADMIN_PROTECTED"); AuthUser after=users.updateStatus(id,status); audit(actor,id,"ACTIVE".equals(status)?"ENABLE_ACCOUNT":"DISABLE_ACCOUNT",before,after); return after; }
    private long activeAdmins(){ Long n=jdbc.queryForObject("select count(*) from app_user where role='ADMIN' and status='ACTIVE'",Long.class); return n==null?0:n; }
    private void requireAdmin(Principal p){ if(p==null||!p.canManageAccounts()) throw new IllegalArgumentException("FORBIDDEN"); }
    private void validate(String u,String p,Role r){ if(u==null||u.isBlank()||u.length()>64||p==null||p.length()<8||r==null) throw new IllegalArgumentException("VALIDATION_ERROR"); }
    private void audit(Principal a,long id,String action,AuthUser before,AuthUser after){ jdbc.update("insert into operator_audit_log(operator_id,target_type,target_id,action,before_snapshot,after_snapshot) values (?, 'USER_ACCOUNT', ?, ?, cast(? as jsonb), cast(? as jsonb))",a.userId(),id,action,snapshot(before),snapshot(after)); }
    private String snapshot(AuthUser u){ return u==null?null:String.format("{\"id\":%d,\"username\":\"%s\",\"role\":\"%s\",\"status\":\"%s\"}",u.id(),u.username(),u.role(),u.status()); }
}

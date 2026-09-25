package com.flashsale.auth;

import com.flashsale.common.security.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public class JdbcAuthUserRepository implements AuthUserRepository {
    private final JdbcTemplate jdbc;
    public JdbcAuthUserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean existsByUsername(String username) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from app_user where username = ?)", Boolean.class, username));
    }
    public AuthUser createCustomer(String username, String passwordHash) {
        return jdbc.queryForObject("insert into app_user(username,password_hash,role,status) values (?, ?, 'CUSTOMER', 'ACTIVE') returning id,username,password_hash,role,status",
                (rs, rowNum) -> new AuthUser(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), Role.valueOf(rs.getString("role")), rs.getString("status")), username, passwordHash);
    }
    public Optional<AuthUser> findByUsername(String username) {
        return jdbc.query("select id,username,password_hash,role,status from app_user where username = ?", rs -> rs.next()
                ? Optional.of(new AuthUser(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), Role.valueOf(rs.getString("role")), rs.getString("status")))
                : Optional.empty(), username);
    }
    public Optional<AuthUser> findById(long id) { return jdbc.query("select id,username,password_hash,role,status from app_user where id = ?", rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), id); }
    public AuthUser create(String username, String passwordHash, Role role) {
        return jdbc.queryForObject("insert into app_user(username,password_hash,role,status) values (?, ?, ?::user_role, 'ACTIVE') returning id,username,password_hash,role,status", (rs,n)->map(rs), username,passwordHash,role.name());
    }
    public AuthUser updateRole(long id, Role role) { return jdbc.queryForObject("update app_user set role = ?::user_role where id = ? returning id,username,password_hash,role,status", (rs,n)->map(rs), role.name(),id); }
    public AuthUser updateStatus(long id, String status) { return jdbc.queryForObject("update app_user set status = ? where id = ? returning id,username,password_hash,role,status", (rs,n)->map(rs), status,id); }
    public List<AuthUser> findAll(int offset, int limit) { return jdbc.query("select id,username,password_hash,role,status from app_user order by id limit ? offset ?", (rs,n)->map(rs), limit,offset); }
    public long count() { return jdbc.queryForObject("select count(*) from app_user", Long.class); }
    public long countActiveAdmins() {
        Long count = jdbc.queryForObject("select count(*) from app_user where role = 'ADMIN' and status = 'ACTIVE'", Long.class);
        return count == null ? 0 : count;
    }
    private AuthUser map(java.sql.ResultSet rs) throws java.sql.SQLException { return new AuthUser(rs.getLong("id"),rs.getString("username"),rs.getString("password_hash"),Role.valueOf(rs.getString("role")),rs.getString("status")); }
}

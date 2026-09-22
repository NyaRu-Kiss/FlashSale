package com.flashsale.auth;

import com.flashsale.common.security.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
}

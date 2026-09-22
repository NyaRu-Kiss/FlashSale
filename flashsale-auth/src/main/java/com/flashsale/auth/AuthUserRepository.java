package com.flashsale.auth;

import java.util.Optional;
import com.flashsale.common.security.Role;

public interface AuthUserRepository {
    boolean existsByUsername(String username);
    AuthUser createCustomer(String username, String passwordHash);
    Optional<AuthUser> findByUsername(String username);
    default Optional<AuthUser> findById(long id) { return Optional.empty(); }
    default AuthUser create(String username, String passwordHash, Role role) { throw new UnsupportedOperationException(); }
    default AuthUser updateRole(long id, Role role) { throw new UnsupportedOperationException(); }
    default AuthUser updateStatus(long id, String status) { throw new UnsupportedOperationException(); }
    default java.util.List<AuthUser> findAll(int offset, int limit) { return java.util.List.of(); }
    default long count() { return 0; }
}

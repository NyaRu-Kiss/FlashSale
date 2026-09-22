package com.flashsale.auth;

import java.util.Optional;

public interface AuthUserRepository {
    boolean existsByUsername(String username);
    AuthUser createCustomer(String username, String passwordHash);
    Optional<AuthUser> findByUsername(String username);
}

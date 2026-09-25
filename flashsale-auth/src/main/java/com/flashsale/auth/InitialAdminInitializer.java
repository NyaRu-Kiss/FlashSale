package com.flashsale.auth;

import com.flashsale.common.security.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates exactly one initial administrator and never mutates an existing account. */
@Component
class InitialAdminInitializer implements ApplicationRunner {
    private final AuthUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    InitialAdminInitializer(
            AuthUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${flashsale.bootstrap.admin.username:}") String username,
            @Value("${flashsale.bootstrap.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (users.countActiveAdmins() > 0) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("ADMIN_USERNAME and ADMIN_PASSWORD are required when no active ADMIN exists");
        }
        if (password.length() < 8) {
            throw new IllegalStateException("ADMIN_PASSWORD must contain at least 8 characters");
        }
        if (users.existsByUsername(username)) {
            throw new IllegalStateException("ADMIN_USERNAME already belongs to a non-admin account");
        }
        users.create(username, passwordEncoder.encode(password), Role.ADMIN);
    }
}

package com.flashsale.auth;

import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Principal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class AuthService {
    private final AuthUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    AuthService(AuthUserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens) { this.users = users; this.passwordEncoder = passwordEncoder; this.tokens = tokens; }
    AuthResult register(String username, String password) {
        validate(username, password);
        if (users.existsByUsername(username)) throw new IllegalArgumentException("USERNAME_ALREADY_EXISTS");
        try {
            AuthUser user = users.createCustomer(username, passwordEncoder.encode(password));
            return new AuthResult(user.id(), user.username(), user.role().name(), null);
        } catch (DataIntegrityViolationException exception) { throw new IllegalArgumentException("USERNAME_ALREADY_EXISTS", exception); }
    }
    AuthResult login(String username, String password) {
        AuthUser user = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("INVALID_CREDENTIALS"));
        if (!user.active() || !passwordEncoder.matches(password, user.passwordHash())) throw new IllegalArgumentException("INVALID_CREDENTIALS");
        return new AuthResult(user.id(), user.username(), user.role().name(), tokens.issue(new Principal(user.id(), user.role())));
    }
    private void validate(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 64 || password == null || password.length() < 8) throw new IllegalArgumentException("VALIDATION_ERROR");
    }
}

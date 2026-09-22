package com.flashsale.auth;

import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @Test void registersCustomerAndIssuesLoginToken() {
        FakeUsers users = new FakeUsers();
        AuthService service = new AuthService(users, new BCryptPasswordEncoder(), new JwtTokenService("01234567890123456789012345678901", Duration.ofHours(1)));
        assertEquals("CUSTOMER", service.register("alice", "password1").role());
        assertNotNull(service.login("alice", "password1").token());
    }
    @Test void rejectsDisabledUser() {
        FakeUsers users = new FakeUsers(); users.user = new AuthUser(1, "alice", new BCryptPasswordEncoder().encode("password1"), Role.CUSTOMER, "DISABLED");
        AuthService service = new AuthService(users, new BCryptPasswordEncoder(), new JwtTokenService("01234567890123456789012345678901", Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> service.login("alice", "password1"));
    }
    static class FakeUsers implements AuthUserRepository {
        AuthUser user;
        public boolean existsByUsername(String username) { return user != null && user.username().equals(username); }
        public AuthUser createCustomer(String username, String passwordHash) { return user = new AuthUser(1, username, passwordHash, Role.CUSTOMER, "ACTIVE"); }
        public Optional<AuthUser> findByUsername(String username) { return user != null && user.username().equals(username) ? Optional.of(user) : Optional.empty(); }
    }
}

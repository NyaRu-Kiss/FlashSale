package com.flashsale.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {
    @Test
    void issuesAndParsesPrincipal() {
        JwtTokenService service = new JwtTokenService("01234567890123456789012345678901", Duration.ofMinutes(5));
        Principal principal = new Principal(7, Role.OPERATOR);
        assertEquals(principal, service.parse(service.issue(principal)));
    }
}

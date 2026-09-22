package com.flashsale.auth;

import com.flashsale.common.security.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Duration;

@Configuration
public class AuthConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean JwtTokenService jwtTokenService() { return new JwtTokenService(System.getenv().getOrDefault("JWT_SECRET", "change-me-development-secret-32-bytes"), Duration.ofHours(2)); }
}

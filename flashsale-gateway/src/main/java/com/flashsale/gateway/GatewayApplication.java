package com.flashsale.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.flashsale.common.security.JwtTokenService;
import java.time.Duration;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) { SpringApplication.run(GatewayApplication.class, args); }
    @Bean JwtTokenService jwtTokenService() { return new JwtTokenService(System.getenv().getOrDefault("JWT_SECRET", "change-me-development-secret-32-bytes"), Duration.ofHours(2)); }
}

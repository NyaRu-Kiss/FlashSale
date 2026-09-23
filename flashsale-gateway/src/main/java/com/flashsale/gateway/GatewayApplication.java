package com.flashsale.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import com.flashsale.common.security.JwtTokenService;
import java.time.Duration;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) { SpringApplication.run(GatewayApplication.class, args); }

    @Bean
    JwtTokenService jwtTokenService(
            @Value("${flashsale.security.jwt-secret}") String secret,
            @Value("${flashsale.security.jwt-ttl:PT2H}") Duration ttl) {
        return new JwtTokenService(secret, ttl);
    }
}

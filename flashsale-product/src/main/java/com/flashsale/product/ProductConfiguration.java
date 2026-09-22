package com.flashsale.product;
import com.flashsale.common.security.JwtTokenService; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration; import java.time.Duration;
@Configuration class ProductConfiguration { @Bean JwtTokenService jwtTokenService(){return new JwtTokenService(System.getenv().getOrDefault("JWT_SECRET","change-me-development-secret-32-bytes"),Duration.ofHours(2));} }

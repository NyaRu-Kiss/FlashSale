package com.flashsale.common.config;

import com.flashsale.common.api.GlobalExceptionHandler;
import com.flashsale.common.metrics.BusinessMetricsConfiguration;
import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.trace.TraceIdFilter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Shared HTTP boundary components explicitly imported by every runnable service. */
@Configuration(proxyBeanMethods = false)
@Import(BusinessMetricsConfiguration.class)
public class CommonWebConfiguration {
    @Bean
    TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    JwtTokenService jwtTokenService(
            @Value("${flashsale.security.jwt-secret}") String secret,
            @Value("${flashsale.security.jwt-ttl:PT2H}") Duration ttl) {
        return new JwtTokenService(secret, ttl);
    }
}

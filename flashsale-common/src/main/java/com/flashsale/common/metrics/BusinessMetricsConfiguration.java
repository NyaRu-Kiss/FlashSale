package com.flashsale.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BusinessMetricsConfiguration {
    @Bean
    BusinessMetrics businessMetrics(MeterRegistry registry) { return new BusinessMetrics(registry); }
}

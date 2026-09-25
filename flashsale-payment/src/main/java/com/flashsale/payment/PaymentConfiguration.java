package com.flashsale.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class PaymentConfiguration {
    @Bean Clock paymentClock() { return Clock.systemUTC(); }
    @Bean PaymentGateway paymentGateway() { return new SimulatedPaymentGateway(); }
    @Bean JdbcPaymentService jdbcPaymentService(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                                PaymentGateway gateway, ObjectMapper json, Clock clock,
                                                com.flashsale.common.metrics.BusinessMetrics metrics) {
        return new JdbcPaymentService(jdbc, manager, gateway, json, clock, metrics);
    }
}

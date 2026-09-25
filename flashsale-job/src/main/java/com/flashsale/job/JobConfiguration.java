package com.flashsale.job;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class JobConfiguration {
    @Bean Clock jobClock() { return Clock.systemUTC(); }
    @Bean CompensationStore compensationStore(JdbcTemplate jdbc) { return new JdbcCompensationStore(jdbc); }
    @Bean CompensationAlert compensationAlert() {
        return record -> LoggerFactory.getLogger(JobConfiguration.class).error("Compensation requires manual attention: key={}, reason={}, error={}", record.key(), record.reason(), record.error());
    }
    @Bean ReconciliationTask reconciliationTask(CompensationStore store, CompensationAlert alert, Clock clock) {
        return new ReconciliationTask(store, alert, clock);
    }
}

package com.flashsale.activity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.flashsale.common.config.CommonWebConfiguration;
import com.flashsale.common.messaging.OutboxDispatchConfiguration;
import com.flashsale.common.cache.CacheInvalidationConsumerConfiguration;

@SpringBootApplication
@Import({CommonWebConfiguration.class, OutboxDispatchConfiguration.class, CacheInvalidationConsumerConfiguration.class})
@EnableScheduling
public class ActivityApplication {
    public static void main(String[] args) { SpringApplication.run(ActivityApplication.class, args); }
}

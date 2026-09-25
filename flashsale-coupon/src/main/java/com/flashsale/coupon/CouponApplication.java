package com.flashsale.coupon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.flashsale.common.config.CommonWebConfiguration;
import com.flashsale.common.messaging.OutboxDispatchConfiguration;
import com.flashsale.common.cache.CacheInvalidationConsumerConfiguration;

@SpringBootApplication
@Import({CommonWebConfiguration.class, OutboxDispatchConfiguration.class, CacheInvalidationConsumerConfiguration.class})
public class CouponApplication {
    public static void main(String[] args) { SpringApplication.run(CouponApplication.class, args); }
}

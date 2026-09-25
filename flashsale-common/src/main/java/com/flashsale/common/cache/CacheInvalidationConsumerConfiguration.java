package com.flashsale.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.messaging.MessageEnvelope;
import com.flashsale.common.messaging.RocketMqConsumerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Each service uses its own group so every local cache can observe invalidations. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.cache-invalidation.consumer.enabled", havingValue = "true")
public class CacheInvalidationConsumerConfiguration {
    @Bean
    CacheInvalidationMessageConsumer cacheInvalidationMessageConsumer(StringRedisTemplate redis) {
        return new CacheInvalidationMessageConsumer(redis);
    }

    @Bean(destroyMethod = "close")
    RocketMqConsumerAdapter cacheInvalidationMqConsumer(CacheInvalidationMessageConsumer handler,
            ObjectMapper json,
            @Value("${spring.application.name}") String service,
            @Value("${flashsale.cache-invalidation.consumer.namesrv-addr:rocketmq-namesrv:9876}") String namesrv,
            @Value("${flashsale.cache-invalidation.consumer.topic:FLASHSALE_EVENTS}") String topic) throws Exception {
        var adapter = new RocketMqConsumerAdapter(service + "-cache-invalidation", namesrv, topic,
                "PRODUCT_CACHE_INVALIDATE || COUPON_CACHE_INVALIDATE || ACTIVITY_CACHE_INVALIDATE",
                body -> decode(body, json), message -> {
                    handler.consume(message);
                    return com.flashsale.common.messaging.ConsumerMessageHandler.HandleResult.SUCCEEDED;
                });
        adapter.start();
        return adapter;
    }

    private static MessageEnvelope decode(byte[] body, ObjectMapper json) {
        try { return json.readValue(body, MessageEnvelope.class); }
        catch (Exception error) { throw new IllegalArgumentException("INVALID_CACHE_INVALIDATION_EVENT", error); }
    }
}

package com.flashsale.common.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(CacheAsideProperties.class)
public class CacheAsideConfiguration {
    @Bean
    CacheInvalidator cacheInvalidator(StringRedisTemplate redis) { return new CacheInvalidator(redis); }

    @Bean
    CacheAsideReader cacheAsideReader(StringRedisTemplate redis, ObjectMapper json, CacheAsideProperties properties) {
        return new CacheAsideReader(redis, json, properties);
    }
}

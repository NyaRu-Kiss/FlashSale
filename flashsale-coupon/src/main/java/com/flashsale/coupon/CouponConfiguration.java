package com.flashsale.coupon;

import com.flashsale.common.cache.CacheAsideProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CacheAsideProperties.class)
final class CouponConfiguration { private CouponConfiguration() {} }

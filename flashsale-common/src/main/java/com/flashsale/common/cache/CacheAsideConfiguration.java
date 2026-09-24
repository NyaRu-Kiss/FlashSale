package com.flashsale.common.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CacheAsideProperties.class)
public class CacheAsideConfiguration { }

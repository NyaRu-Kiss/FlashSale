package com.flashsale.order;

import com.flashsale.common.trace.TraceContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared headers for future order-domain Feign adapters; business clients belong to R24. */
@Configuration(proxyBeanMethods = false)
class FeignPropagationConfiguration {
    @Bean
    RequestInterceptor requestContextPropagation() {
        return (RequestTemplate template) -> {
            String authorization = org.springframework.web.context.request.RequestContextHolder
                    .getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attributes
                    ? attributes.getRequest().getHeader("Authorization") : null;
            if (authorization != null && !authorization.isBlank()) template.header("Authorization", authorization);
            template.header(TraceContext.HEADER, TraceContext.getOrCreate());
        };
    }
}

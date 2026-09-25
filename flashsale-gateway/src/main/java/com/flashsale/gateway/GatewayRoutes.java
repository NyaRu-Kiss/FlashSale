package com.flashsale.gateway;

import java.util.List;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class GatewayRoutes {
    @Bean
    RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth", r -> r.path("/api/v1/auth/**", "/api/v1/admin/users/**")
                        .uri("lb://flashsale-auth"))
                .route("product", r -> r.path("/api/v1/products/**", "/api/v1/admin/products/**")
                        .uri("lb://flashsale-product"))
                .route("activity", r -> r.path("/api/v1/activities/**", "/api/v1/admin/activities/**")
                        .uri("lb://flashsale-activity"))
                .route("coupon", r -> r.path("/api/v1/coupons/**", "/api/v1/coupon-templates/**",
                                "/api/v1/admin/coupon-templates/**")
                        .uri("lb://flashsale-coupon"))
                .route("payment", r -> r.path("/api/v1/orders/*/payments/**", "/api/v1/payments/**")
                        .uri("lb://flashsale-payment"))
                .route("inventory", r -> r.path("/api/v1/inventory/**")
                        .uri("lb://flashsale-inventory"))
                .route("order", r -> r.path("/api/v1/orders/**").uri("lb://flashsale-order"))
                .build();
    }

    @Bean
    CorsWebFilter corsWebFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(HttpHeaders.LOCATION, "X-Trace-Id"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return new CorsWebFilter(source);
    }
}

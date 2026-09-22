package com.flashsale.gateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GatewayRoutes implements WebMvcConfigurer {
    @Bean public WebMvcConfigurer gatewayCors(){ return new WebMvcConfigurer(){ @Override public void addCorsMappings(CorsRegistry r){ r.addMapping("/api/**").allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS").allowedOrigins("*"); } }; }
}

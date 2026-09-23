package com.flashsale.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.flashsale.common.config.CommonWebConfiguration;

@SpringBootApplication
@EnableFeignClients
@Import(CommonWebConfiguration.class)
public class OrderApplication {
    public static void main(String[] args) { SpringApplication.run(OrderApplication.class, args); }
}

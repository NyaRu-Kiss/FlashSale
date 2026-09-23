package com.flashsale.activity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.flashsale.common.config.CommonWebConfiguration;

@SpringBootApplication
@Import(CommonWebConfiguration.class)
public class ActivityApplication {
    public static void main(String[] args) { SpringApplication.run(ActivityApplication.class, args); }
}

package com.flashsale.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.flashsale.common.config.CommonWebConfiguration;

@SpringBootApplication
@Import({CommonWebConfiguration.class, com.flashsale.common.job.XxlJobExecutorConfiguration.class, JobConfiguration.class})
public class JobApplication {
    public static void main(String[] args) { SpringApplication.run(JobApplication.class, args); }
}

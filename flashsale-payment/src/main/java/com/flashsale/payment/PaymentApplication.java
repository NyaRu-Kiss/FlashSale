package com.flashsale.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.flashsale.common.config.CommonWebConfiguration;
import com.flashsale.common.messaging.OutboxDispatchConfiguration;
import com.flashsale.common.job.XxlJobExecutorConfiguration;

@SpringBootApplication
@Import({CommonWebConfiguration.class, OutboxDispatchConfiguration.class, XxlJobExecutorConfiguration.class})
public class PaymentApplication {
    public static void main(String[] args) { SpringApplication.run(PaymentApplication.class, args); }
}

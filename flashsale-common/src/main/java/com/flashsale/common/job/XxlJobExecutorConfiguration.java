package com.flashsale.common.job;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers a real XXL-Job executor; scheduling remains external to the service JVM. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.xxl.enabled", havingValue = "true")
public class XxlJobExecutorConfiguration {
    @Bean(destroyMethod = "destroy")
    XxlJobSpringExecutor xxlJobExecutor(
            @Value("${flashsale.xxl.admin-addresses:}") String adminAddresses,
            @Value("${flashsale.xxl.app-name:${spring.application.name}}") String appName,
            @Value("${flashsale.xxl.access-token:}") String accessToken,
            @Value("${flashsale.xxl.address:}") String address,
            @Value("${flashsale.xxl.ip:}") String ip,
            @Value("${flashsale.xxl.port:9999}") int port,
            @Value("${flashsale.xxl.log-path:/tmp/flashsale-xxl-job}") String logPath,
            @Value("${flashsale.xxl.log-retention-days:7}") int retentionDays) {
        if (adminAddresses == null || adminAddresses.isBlank()) {
            throw new IllegalStateException("flashsale.xxl.admin-addresses must be configured when XXL-Job is enabled");
        }
        var executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appName);
        executor.setAccessToken(accessToken);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(retentionDays);
        return executor;
    }
}

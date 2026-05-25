package com.chen404.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB 执行器配置。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
@ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
public class XxlJobConfig {

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        XxlJobProperties.Executor executorProperties = properties.getExecutor();

        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAppname(executorProperties.getAppName());
        executor.setAddress(executorProperties.getAddress());
        executor.setIp(executorProperties.getIp());
        executor.setPort(executorProperties.getPort());
        executor.setAccessToken(properties.getAccessToken());
        executor.setLogPath(executorProperties.getLogPath());
        executor.setLogRetentionDays(executorProperties.getLogRetentionDays());

        log.info("[XXL_JOB_EXECUTOR_INIT] appName={} port={} logPath={}",
                executorProperties.getAppName(),
                executorProperties.getPort(),
                executorProperties.getLogPath());
        return executor;
    }
}

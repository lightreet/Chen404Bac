package com.chen404.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XXL-JOB 执行器配置属性。
 */
@Data
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    /**
     * 是否启用 XXL-JOB 执行器。
     */
    private boolean enabled = false;

    /**
     * 调度中心地址，多个地址用逗号分隔。
     */
    private String adminAddresses;

    /**
     * 调度中心与执行器通讯令牌。
     */
    private String accessToken;

    /**
     * 执行器配置。
     */
    private Executor executor = new Executor();

    @Data
    public static class Executor {

        /**
         * 执行器 AppName，需要与调度中心执行器配置一致。
         */
        private String appName = "chen404-bac";

        /**
         * 执行器注册地址，为空时由 XXL-JOB 自动组合。
         */
        private String address = "";

        /**
         * 执行器 IP，为空时自动获取。
         */
        private String ip = "";

        /**
         * 执行器端口。
         */
        private int port = 9999;

        /**
         * 执行日志目录。
         */
        private String logPath = "./logs/xxl-job";

        /**
         * 日志保留天数。
         */
        private int logRetentionDays = 30;
    }
}

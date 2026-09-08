package com.chen404.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 本地持久化清理队列不依赖可选的 XXL-JOB 服务。 */
@Configuration
@EnableScheduling
public class FileDeletionTaskConfig {
}

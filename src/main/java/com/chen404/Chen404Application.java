package com.chen404;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.chen404.mapper")
@ServletComponentScan("com.chen404.filter")
@EnableAsync
@EnableScheduling
public class Chen404Application {

    public static void main(String[] args) {
        var context = SpringApplication.run(Chen404Application.class, args);
        Environment env = context.getEnvironment();

        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                            ║");
        System.out.println("║         Chen404 Backend 启动成功！                         ║");
        System.out.println("║                                                            ║");
        System.out.println("║   本地访问: http://localhost:" + port + contextPath + "                            ║");
        System.out.println("║   API 文档: http://localhost:" + port + contextPath + "/swagger-ui.html     ║");
        System.out.println("║                                                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }
}

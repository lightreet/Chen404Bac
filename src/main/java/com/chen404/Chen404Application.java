package com.chen404;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@MapperScan("com.chen404.mapper")
@ServletComponentScan("com.chen404.filter")
public class Chen404Application {

    public static void main(String[] args) {
        SpringApplication.run(Chen404Application.class, args);
        System.out.println("=================================");
        System.out.println("Chen404 Backend Started Successfully!");
        System.out.println("API URL: http://localhost:8080/api");
        System.out.println("=================================");
    }
}

package com.cloudai.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.cloudai")
public class CloudAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudAiApplication.class, args);
    }
}
package com.example.xpxu.wolf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class WolfApplication {

    public static void main(String[] args) {
        SpringApplication.run(WolfApplication.class, args);
    }
}

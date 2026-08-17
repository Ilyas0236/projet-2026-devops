package com.wydad.digital.gamification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GamificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(GamificationApplication.class, args);
    }
}

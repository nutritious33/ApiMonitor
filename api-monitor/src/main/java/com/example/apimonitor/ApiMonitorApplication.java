package com.example.apimonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Exclude the default in-memory UserDetailsService — we use API key auth via ApiKeyAuthFilter
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
public class ApiMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiMonitorApplication.class, args);
    }

}
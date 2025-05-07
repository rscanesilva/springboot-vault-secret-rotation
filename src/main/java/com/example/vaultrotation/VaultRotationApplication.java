package com.example.vaultrotation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@RefreshScope
public class VaultRotationApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultRotationApplication.class, args);
    }
} 
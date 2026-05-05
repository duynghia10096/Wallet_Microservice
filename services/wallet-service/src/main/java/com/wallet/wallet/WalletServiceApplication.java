package com.wallet.wallet;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Wallet Service - Spring Boot Application Entry Point
 * 
 * Port: 8082
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@EnableJpaRepositories(basePackages = "com.wallet.wallet.domain.repository")
public class WalletServiceApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}

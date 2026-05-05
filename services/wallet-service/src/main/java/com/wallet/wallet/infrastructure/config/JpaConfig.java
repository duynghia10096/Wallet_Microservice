package com.wallet.wallet.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA Configuration for Wallet Service
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.wallet.wallet.infrastructure.persistence",
        repositoryImplementationPostfix = "Impl"
)
@EnableTransactionManagement
public class JpaConfig {
}

package com.wallet.wallet.infrastructure.client;

import com.wallet.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Feign client to call Transaction Service synchronously.
 * Used for: querying daily transaction totals for limit enforcement.
 *
 * Name must match spring.application.name of transaction-service.
 * Eureka handles the load balancing (lb://transaction-service).
 */
@FeignClient(
    name = "transaction-service",
    fallback = TransactionServiceClientFallback.class
)
public interface TransactionServiceClient {

    @GetMapping("/v1/transactions/daily-total")
    ApiResponse<BigDecimal> getDailyTotal(
            @RequestParam("walletId") UUID walletId,
            @RequestParam("currency") String currency
    );
}

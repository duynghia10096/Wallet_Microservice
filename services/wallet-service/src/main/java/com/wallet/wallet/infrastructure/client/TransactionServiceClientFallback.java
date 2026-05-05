package com.wallet.wallet.infrastructure.client;

import com.wallet.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fallback when Transaction Service is unavailable.
 * Returns ZERO so transfer can proceed - we accept the risk
 * rather than blocking the user completely (fail-open strategy).
 * In production: consider fail-closed for large amounts.
 */
@Slf4j
@Component
public class TransactionServiceClientFallback implements TransactionServiceClient {

    @Override
    public ApiResponse<BigDecimal> getDailyTotal(UUID walletId, String currency) {
        log.warn("Transaction service unavailable, using fallback daily total = 0 for wallet: {}", walletId);
        return ApiResponse.success(BigDecimal.ZERO, "fallback");
    }
}

package com.wallet.transaction.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Feign client: Transaction Service → Wallet Service (synchronous REST call).
 *
 * Used for:
 * - Validating wallet exists and is active
 * - Crediting/debiting wallet balances during a transaction
 *
 * Feign + Eureka: "wallet-service" resolves via service discovery.
 * No hardcoded URLs!
 */
@FeignClient(
    name = "wallet-service",
    fallback = WalletServiceClientFallback.class
)
public interface WalletServiceClient {

    @GetMapping("/api/v1/wallets/{walletId}")
    WalletResponse getWallet(@PathVariable("walletId") UUID walletId);

    @PostMapping("/api/v1/wallets/{walletId}/credit")
    WalletResponse credit(
            @PathVariable("walletId") UUID walletId,
            @RequestBody BalanceChangeRequest request);

    @PostMapping("/api/v1/wallets/{walletId}/debit")
    WalletResponse debit(
            @PathVariable("walletId") UUID walletId,
            @RequestBody BalanceChangeRequest request);
}

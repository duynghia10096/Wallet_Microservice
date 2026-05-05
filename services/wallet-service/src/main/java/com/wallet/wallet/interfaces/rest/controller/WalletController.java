package com.wallet.wallet.interfaces.rest.controller;

import com.wallet.wallet.application.dto.WalletDto;
import com.wallet.wallet.application.service.WalletApplicationService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Wallet REST Controller
 * 
 * Endpoints:
 * - GET /api/v1/wallets/{id} - Lấy chi tiết ví
 * - POST /api/v1/wallets/{id}/debit - Trừ tiền
 * - POST /api/v1/wallets/{id}/credit - Cộng tiền
 * - GET /api/v1/wallets/{id}/balance - Kiểm tra số dư
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {
    private final WalletApplicationService walletApplicationService;


    @GetMapping("/{walletId}")
    public ResponseEntity<WalletDto> getWallet(@PathVariable("walletId") String walletId) {
        log.info("Getting wallet: {}", walletId);
        try {
            WalletDto wallet = walletApplicationService.getWallet(java.util.UUID.fromString(walletId));
            return ResponseEntity.ok(wallet);
        } catch (Exception e) {
            log.error("Error fetching wallet", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/{walletId}/balance")
    public ResponseEntity<String> getBalance(@PathVariable("walletId") String walletId) {
        log.info("Getting balance for wallet: {}", walletId);
        try {
            WalletDto wallet = walletApplicationService.getWallet(java.util.UUID.fromString(walletId));
            return ResponseEntity.ok(wallet.getBalance().toString());
        } catch (Exception e) {
            log.error("Error fetching balance", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/{walletId}/debit")
    public ResponseEntity<WalletDto> debitWallet(
            @PathVariable("walletId") String walletId,
            @RequestBody DebitRequest request) {
        log.info("Debiting wallet: {} with amount: {}", walletId, request.getAmount());
        try {
            WalletDto wallet = walletApplicationService.debit(
                    java.util.UUID.fromString(walletId), 
                    request.getAmount(), 
                    request.getCurrency(),
                    "Debit transaction"
            );
            return ResponseEntity.ok(wallet);
        } catch (Exception e) {
            log.error("Error debiting wallet", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/{walletId}/credit")
    public ResponseEntity<WalletDto> creditWallet(
            @PathVariable("walletId") String walletId,
            @RequestBody CreditRequest request) {
        log.info("Crediting wallet: {} with amount: {}", walletId, request.getAmount());
        try {
            WalletDto wallet = walletApplicationService.credit(
                    java.util.UUID.fromString(walletId), 
                    request.getAmount(), 
                    request.getCurrency(),
                    "Credit transaction"
            );
            return ResponseEntity.ok(wallet);
        } catch (Exception e) {
            log.error("Error crediting wallet", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebitRequest {
        private java.math.BigDecimal amount;
        private String currency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditRequest {
        private java.math.BigDecimal amount;
        private String currency;
    }
}

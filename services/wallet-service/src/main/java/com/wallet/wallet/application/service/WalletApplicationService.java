package com.wallet.wallet.application.service;

import com.wallet.common.event.WalletCreatedEvent;
import com.wallet.wallet.application.dto.*;
import com.wallet.wallet.domain.exception.WalletNotFoundException;
import com.wallet.wallet.domain.model.*;
import com.wallet.wallet.domain.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
// @RequiredArgsConstructor
public class WalletApplicationService {

    private final WalletRepository walletRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WalletApplicationService(WalletRepository walletRepository,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.walletRepository = walletRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Called by Kafka consumer when user.registered event arrives.
     * Every new user gets a default personal VND wallet automatically.
     */
    @Transactional
    public WalletDto createDefaultWalletForUser(UUID userId, String email) {
        // Idempotent - skip if wallet already exists for user
        if (!walletRepository.findByUserId(userId).isEmpty()) {
            log.info("Wallet already exists for user: {}", userId);
            return null;
        }

        Wallet wallet = Wallet.create(userId, WalletType.PERSONAL, "VND");
        wallet.assignWalletNumber(generateWalletNumber());

        Wallet saved = walletRepository.save(wallet);

        kafkaTemplate.send(WalletCreatedEvent.TOPIC, saved.getId().toString(),
                WalletCreatedEvent.builder()
                        .walletId(saved.getId())
                        .userId(userId)
                        .walletNumber(saved.getWalletNumber())
                        .walletType(saved.getWalletType().name())
                        .currency(saved.getCurrency())
                        .occurredAt(LocalDateTime.now())
                        .build());

        return toDto(saved);
    }

    @Transactional
    public WalletDto createWallet(UUID userId, String walletType, String currency) {
        Wallet wallet = Wallet.create(userId, WalletType.valueOf(walletType), currency);
        wallet.assignWalletNumber(generateWalletNumber());
        return toDto(walletRepository.save(wallet));
    }

    @Transactional(readOnly = true)
    public WalletDto getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .map(this::toDto)
                .orElseThrow(() -> new WalletNotFoundException(walletId.toString()));
    }

    @Transactional(readOnly = true)
    public List<WalletDto> getUserWallets(UUID userId) {
        return walletRepository.findByUserId(userId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public WalletDto credit(UUID walletId, BigDecimal amount, String currency, String reason) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId.toString()));
        wallet.credit(com.wallet.wallet.domain.valueobject.Money.of(amount, currency), reason);
        return toDto(walletRepository.save(wallet));
    }

    @Transactional
    public WalletDto debit(UUID walletId, BigDecimal amount, String currency, String reason) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId.toString()));
        wallet.debit(com.wallet.wallet.domain.valueobject.Money.of(amount, currency), reason);
        return toDto(walletRepository.save(wallet));
    }

    private String generateWalletNumber() {
        return "WL" + String.format("%010d", System.nanoTime() % 10_000_000_000L);
    }

    private WalletDto toDto(Wallet w) {
        return WalletDto.builder()
                .id(w.getId())
                .walletNumber(w.getWalletNumber())
                .userId(w.getUserId())
                .walletType(w.getWalletType().name())
                .currency(w.getCurrency())
                .balance(w.getBalance().getAmount())
                .availableBalance(w.getAvailableBalance().getAmount())
                .frozenBalance(w.getFrozenBalance().getAmount())
                .status(w.getStatus().name())
                .createdAt(w.getCreatedAt())
                .build();
    }
}

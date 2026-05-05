package com.wallet.transaction.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    private UUID id;
    private String transactionRef;
    private String idempotencyKey;
    private UUID sourceWalletId;
    private UUID destWalletId;
    private UUID userId;
    private BigDecimal amount;
    private BigDecimal feeAmount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    private String description;
    private String failureReason;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    @Version
    private long version;

    protected Transaction() {
    }

    public static Transaction create(UUID sourceWalletId, UUID destWalletId, UUID userId,
            BigDecimal amount, BigDecimal feeAmount, String currency,
            TransactionType type, String idempotencyKey, String description) {
        Transaction t = new Transaction();
        t.id = UUID.randomUUID();
        t.transactionRef = "TXN" + System.currentTimeMillis();
        t.sourceWalletId = sourceWalletId;
        t.destWalletId = destWalletId;
        t.userId = userId;
        t.amount = amount;
        t.feeAmount = feeAmount != null ? feeAmount : BigDecimal.ZERO;
        t.currency = currency;
        t.type = type;
        t.idempotencyKey = idempotencyKey;
        t.description = description;
        t.status = TransactionStatus.PENDING;
        t.initiatedAt = LocalDateTime.now();
        t.version = 0;
        return t;
    }

    public static Transaction reconstitute(UUID id, String transactionRef, String idempotencyKey,
            UUID sourceWalletId, UUID destWalletId, UUID userId,
            BigDecimal amount, BigDecimal feeAmount, String currency,
            TransactionType type, TransactionStatus status, String description,
            String failureReason, LocalDateTime initiatedAt, LocalDateTime completedAt, long version) {
        Transaction t = new Transaction();
        t.id = id;
        t.transactionRef = transactionRef;
        t.idempotencyKey = idempotencyKey;
        t.sourceWalletId = sourceWalletId;
        t.destWalletId = destWalletId;
        t.userId = userId;
        t.amount = amount;
        t.feeAmount = feeAmount;
        t.currency = currency;
        t.type = type;
        t.status = status;
        t.description = description;
        t.failureReason = failureReason;
        t.initiatedAt = initiatedAt;
        t.completedAt = completedAt;
        t.version = version;
        return t;
    }

    public void complete() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
    }
}

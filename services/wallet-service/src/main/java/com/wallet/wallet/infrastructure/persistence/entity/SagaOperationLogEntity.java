package com.wallet.wallet.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records every saga operation applied to a wallet.
 *
 * Purpose: idempotency.
 * If the coordinator retries a command (due to timeout or recovery),
 * wallet-service checks this table first.
 * If a record with (transactionId, operationType) already exists → skip, reply OK.
 *
 * OperationType values:
 *  DEBIT    — source wallet was debited
 *  CREDIT   — dest wallet was credited
 *  REVERSED — source wallet was refunded (compensation)
 */
@Entity
@Table(
    name = "saga_operation_logs",
    uniqueConstraints = {
        // One operation type per transaction — prevents double-apply
        @UniqueConstraint(
            name = "uq_saga_op_tx_type",
            columnNames = {"transaction_id", "operation_type"}
        )
    },
    indexes = {
        @Index(name = "idx_saga_op_transaction", columnList = "transaction_id"),
        @Index(name = "idx_saga_op_saga",        columnList = "saga_id"),
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SagaOperationLogEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    /** DEBIT | CREDIT | REVERSED */
    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

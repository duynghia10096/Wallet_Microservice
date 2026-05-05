package com.wallet.transaction.saga.state;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted checkpoint of each saga execution.
 *
 * Why persist?
 * ─ If coordinator crashes mid-saga, recovery job can resume from last known state.
 * ─ Full audit trail: who transferred what, which step failed, how many retries.
 * ─ Ops team can query saga_instances to debug production issues instantly.
 *
 * One SagaInstance = One transfer attempt.
 */
@Entity
@Table(
    name = "saga_instances",
    indexes = {
        @Index(name = "idx_saga_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_saga_state",          columnList = "state"),
        @Index(name = "idx_saga_user_id",         columnList = "user_id"),
        @Index(name = "idx_saga_last_step",       columnList = "last_step_at"),
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaInstance {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** FK to transactions table — 1:1 */
    @Column(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private UUID transactionId;

    @Column(name = "transaction_ref", nullable = false, updatable = false, length = 50)
    private String transactionRef;

    @Column(name = "source_wallet_id", nullable = false, updatable = false)
    private UUID sourceWalletId;

    @Column(name = "dest_wallet_id", nullable = false, updatable = false)
    private UUID destWalletId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Principal amount (fee is stored separately) */
    @Column(name = "amount", nullable = false, updatable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "fee_amount", nullable = false, updatable = false, precision = 20, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    // ── Mutable state fields ────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private SagaState state;

    /** Failure reason captured at the step that failed */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Which step we're retrying (for the recovery job) */
    @Column(name = "current_step", length = 50)
    private String currentStep;

    /** How many times this step was retried */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_step_at")
    private LocalDateTime lastStepAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Optimistic lock — prevents concurrent updates to same saga */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ── State transition methods ──────────────────────────────────────

    public void transitionTo(SagaState newState, String step) {
        this.state       = newState;
        this.currentStep = step;
        this.lastStepAt  = LocalDateTime.now();
        this.retryCount  = 0; // reset retry counter on each step advance
    }

    public void markFailed(String reason) {
        this.state         = SagaState.FAILED;
        this.failureReason = reason;
        this.lastStepAt    = LocalDateTime.now();
    }

    public void startCompensation(String reason) {
        this.state         = SagaState.REVERSING_SOURCE_DEBIT;
        this.currentStep   = "REVERSE_SOURCE_DEBIT";
        this.failureReason = reason;
        this.lastStepAt    = LocalDateTime.now();
        this.retryCount    = 0;
    }

    public void markCompensated() {
        this.state      = SagaState.COMPENSATED;
        this.lastStepAt = LocalDateTime.now();
    }

    public void markCompensationFailed(String reason) {
        this.state         = SagaState.COMPENSATION_FAILED;
        this.failureReason = (this.failureReason != null ? this.failureReason + " | " : "")
                           + "Compensation failed: " + reason;
        this.lastStepAt    = LocalDateTime.now();
    }

    public void markTimedOut() {
        this.state      = SagaState.TIMED_OUT;
        this.lastStepAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.state      = SagaState.COMPLETED;
        this.lastStepAt = LocalDateTime.now();
    }

    public void incrementRetry() {
        this.retryCount++;
        this.lastStepAt = LocalDateTime.now();
    }

    public BigDecimal totalDebitAmount() {
        return amount.add(feeAmount);
    }
}

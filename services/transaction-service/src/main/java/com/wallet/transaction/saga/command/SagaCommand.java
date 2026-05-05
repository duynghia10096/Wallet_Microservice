package com.wallet.transaction.saga.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Commands sent from Coordinator → Workers (wallet-service).
 *
 * Naming convention:
 * Commands: imperative verb ("DebitSourceCommand", "CreditDestCommand")
 * Replies: past tense ("DebitSourceReply", "CreditDestReply")
 *
 * Topics:
 * saga.cmd.* — coordinator → worker (command channel)
 * saga.reply.* — worker → coordinator (reply channel)
 *
 * Every command carries sagaId so the coordinator can correlate replies
 * back to the correct SagaInstance even when processing thousands of sagas
 * concurrently.
 */
public final class SagaCommand {

    // ── Topic constants ───────────────────────────────────────────────────
    public static final String DEBIT_SOURCE_CMD = "saga.cmd.debit-source";
    public static final String CREDIT_DEST_CMD = "saga.cmd.credit-dest";
    public static final String REVERSE_DEBIT_CMD = "saga.cmd.reverse-source-debit";

    public static final String DEBIT_SOURCE_REPLY = "saga.reply.debit-source";
    public static final String CREDIT_DEST_REPLY = "saga.reply.credit-dest";
    public static final String REVERSE_DEBIT_REPLY = "saga.reply.reverse-source-debit";

    // ── Step 1 Command ────────────────────────────────────────────────────

    /**
     * Coordinator → wallet-service:
     * "Debit this wallet by (amount + fee). Reply with success/fail."
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebitSourceCommand {
        private UUID sagaId;
        private UUID transactionId;
        private UUID walletId;
        private BigDecimal amount; // principal + fee
        private String currency;
        private String description;
        private LocalDateTime issuedAt;
    }

    // ── Step 2 Command ────────────────────────────────────────────────────

    /**
     * Coordinator → wallet-service:
     * "Credit this wallet by (amount only, no fee). Reply with success/fail."
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditDestCommand {
        private UUID sagaId;
        private UUID transactionId;
        private UUID walletId;
        private BigDecimal amount; // principal only — fee stays in platform
        private String currency;
        private String description;
        private LocalDateTime issuedAt;
    }

    // ── Compensation Command ──────────────────────────────────────────────

    /**
     * Coordinator → wallet-service:
     * "Refund (amount + fee) back to source wallet. This is a compensation."
     *
     * MUST be idempotent: if delivered twice, wallet should only be credited once.
     * Use transactionId as idempotency key in wallet-service.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReverseSourceDebitCommand {
        private UUID sagaId;
        private UUID transactionId; // used as idempotency key
        private UUID walletId;
        private BigDecimal amount; // same amount that was debited
        private String currency;
        private String compensationReason;
        private LocalDateTime issuedAt;
    }

    // ── Reply (shared structure for all steps) ───────────────────────────

    /**
     * Worker → Coordinator reply for any command.
     * stepName tells coordinator which step this reply is for,
     * in case a delayed reply arrives after a retry was issued.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandReply {
        private UUID sagaId;
        private UUID transactionId;
        private String stepName; // "DEBIT_SOURCE" | "CREDIT_DEST" | "REVERSE_DEBIT"
        private boolean success;
        private String failureReason;
        private BigDecimal newBalance; // for audit/logging
        private LocalDateTime repliedAt;
    }

    private SagaCommand() {
    }
}

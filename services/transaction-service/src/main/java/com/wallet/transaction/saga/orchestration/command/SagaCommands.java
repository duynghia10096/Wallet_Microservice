package com.wallet.transaction.saga.orchestration.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Commands sent from Orchestrator → wallet-service workers.
 * Topics are COMMAND channels (not event channels).
 *
 * Orchestration pattern uses:
 * - COMMAND topics: coordinator → worker (tell what to do)
 * - REPLY topics:   worker → coordinator (report result)
 */
public final class SagaCommands {

    public static final String DEBIT_SOURCE_CMD    = "saga.cmd.wallet.debit-source";
    public static final String CREDIT_DEST_CMD     = "saga.cmd.wallet.credit-dest";
    public static final String REVERSE_DEBIT_CMD   = "saga.cmd.wallet.reverse-debit";

    public static final String DEBIT_REPLY         = "saga.reply.wallet.debit";
    public static final String CREDIT_REPLY        = "saga.reply.wallet.credit";
    public static final String REVERSE_REPLY       = "saga.reply.wallet.reverse";

    /**
     * Coordinator → wallet-service: "Please debit this wallet"
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DebitSourceCommand {
        private UUID sagaId;
        private UUID transactionId;
        private UUID walletId;
        private BigDecimal amount;        // principal + fee
        private String currency;
        private String reason;
        private LocalDateTime issuedAt;
    }

    /**
     * Coordinator → wallet-service: "Please credit this wallet"
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreditDestCommand {
        private UUID sagaId;
        private UUID transactionId;
        private UUID walletId;
        private BigDecimal amount;        // principal only
        private String currency;
        private String reason;
        private LocalDateTime issuedAt;
    }

    /**
     * Coordinator → wallet-service: "Please reverse the debit (compensation)"
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReverseDebitCommand {
        private UUID sagaId;
        private UUID transactionId;
        private UUID walletId;
        private BigDecimal amount;
        private String currency;
        private String reason;
        private LocalDateTime issuedAt;
    }

    /**
     * wallet-service → coordinator: result of command execution
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CommandReply {
        private UUID sagaId;
        private UUID transactionId;
        private String commandType;       // DEBIT / CREDIT / REVERSE
        private boolean success;
        private String failureReason;
        private BigDecimal balanceAfter;
        private LocalDateTime repliedAt;
    }

    private SagaCommands() {}
}

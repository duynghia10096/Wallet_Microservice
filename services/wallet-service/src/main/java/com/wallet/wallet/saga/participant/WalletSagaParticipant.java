package com.wallet.wallet.saga.participant;

import com.wallet.wallet.domain.exception.InsufficientBalanceException;
import com.wallet.wallet.domain.model.Wallet;
import com.wallet.wallet.domain.repository.WalletRepository;
import com.wallet.wallet.domain.valueobject.Money;
import com.wallet.wallet.infrastructure.persistence.repository.SagaCompensationLogRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════════════
 * WALLET SERVICE — Saga Participant (Worker)
 * ══════════════════════════════════════════════════════════════════════
 *
 * This component makes wallet-service a participant in the orchestration saga.
 * It is PASSIVE: receives commands from the coordinator, executes them,
 * and replies with the result. It has zero knowledge of the overall flow.
 *
 * Handles 3 commands:
 *  1. DebitSourceCommand      → debit wallet, reply
 *  2. CreditDestCommand       → credit wallet, reply
 *  3. ReverseSourceDebitCommand → refund wallet (compensation), reply
 *
 * ── Idempotency ────────────────────────────────────────────────────
 * Each command uses transactionId as idempotency key.
 * If the coordinator retries (due to recovery job or timeout),
 * the wallet-service must NOT apply the operation twice.
 *
 * Strategy:
 *  Debit:  check if SagaCompensationLog has a record for this transactionId
 *          with type=DEBIT before applying.
 *  Credit: same check with type=CREDIT.
 *  Reverse: check log type=REVERSED — only apply once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletSagaParticipant {

    private final WalletRepository              walletRepository;
    private final KafkaTemplate<String, Object> kafka;
    private final SagaCompensationLogRepository compensationLog;

    private static final String DEBIT_CMD    = "saga.cmd.debit-source";
    private static final String CREDIT_CMD   = "saga.cmd.credit-dest";
    private static final String REVERSE_CMD  = "saga.cmd.reverse-source-debit";

    private static final String DEBIT_REPLY  = "saga.reply.debit-source";
    private static final String CREDIT_REPLY = "saga.reply.credit-dest";
    private static final String REVERSE_REPLY= "saga.reply.reverse-source-debit";

    // ══════════════════════════════════════════════════════════════════
    // STEP 1: Handle DebitSourceCommand
    // ══════════════════════════════════════════════════════════════════

    @KafkaListener(topics = DEBIT_CMD, groupId = "wallet-saga-participant-group")
    @Transactional
    public void onDebitSourceCommand(@Payload DebitSourceCmd cmd, Acknowledgment ack) {
        log.info("[WALLET-SAGA][{}] Received DebitSourceCommand walletId={} amount={}",
                cmd.getSagaId(), cmd.getWalletId(), cmd.getAmount());

        // ── Idempotency check ──────────────────────────────────────
        if (compensationLog.existsByTransactionIdAndOperationType(cmd.getTransactionId(), "DEBIT")) {
            log.warn("[WALLET-SAGA][{}] Duplicate DebitSourceCommand — already applied, re-sending OK reply",
                    cmd.getSagaId());
            sendReply(DEBIT_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "DEBIT_SOURCE", true, null, null);
            ack.acknowledge();
            return;
        }

        try {
            Wallet wallet = walletRepository.findByIdForUpdate(cmd.getWalletId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Source wallet not found: " + cmd.getWalletId()));

            if (!wallet.isActive()) {
                throw new IllegalStateException("Source wallet is not active: " + cmd.getWalletId());
            }

            wallet.debit(Money.of(cmd.getAmount(), cmd.getCurrency()), cmd.getDescription());
            walletRepository.save(wallet);

            // Record operation for idempotency
            compensationLog.save(buildLog(cmd.getTransactionId(), cmd.getSagaId(), "DEBIT",
                    cmd.getWalletId(), cmd.getAmount(), cmd.getCurrency()));

            sendReply(DEBIT_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "DEBIT_SOURCE", true, null, wallet.getAvailableBalance().getAmount());

            log.info("[WALLET-SAGA][{}] Debit OK — walletId={} newBalance={}",
                    cmd.getSagaId(), cmd.getWalletId(), wallet.getAvailableBalance().getAmount());

        } catch (InsufficientBalanceException e) {
            log.warn("[WALLET-SAGA][{}] Insufficient balance in wallet={}", cmd.getSagaId(), cmd.getWalletId());
            sendReply(DEBIT_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "DEBIT_SOURCE", false, "Insufficient balance", null);

        } catch (Exception e) {
            log.error("[WALLET-SAGA][{}] Debit failed: {}", cmd.getSagaId(), e.getMessage(), e);
            sendReply(DEBIT_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "DEBIT_SOURCE", false, e.getMessage(), null);
        }

        ack.acknowledge();
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 2: Handle CreditDestCommand
    // ══════════════════════════════════════════════════════════════════

    @KafkaListener(topics = CREDIT_CMD, groupId = "wallet-saga-participant-group")
    @Transactional
    public void onCreditDestCommand(@Payload CreditDestCmd cmd, Acknowledgment ack) {
        log.info("[WALLET-SAGA][{}] Received CreditDestCommand walletId={} amount={}",
                cmd.getSagaId(), cmd.getWalletId(), cmd.getAmount());

        // ── Idempotency check ──────────────────────────────────────
        if (compensationLog.existsByTransactionIdAndOperationType(cmd.getTransactionId(), "CREDIT")) {
            log.warn("[WALLET-SAGA][{}] Duplicate CreditDestCommand — already applied, re-sending OK reply",
                    cmd.getSagaId());
            sendReply(CREDIT_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "CREDIT_DEST", true, null, null);
            ack.acknowledge();
            return;
        }

        try {
            Wallet wallet = walletRepository.findByIdForUpdate(cmd.getWalletId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Dest wallet not found: " + cmd.getWalletId()));

            // Note: we credit even if wallet is suspended — money must land somewhere.
            // In production: if wallet is closed, route to suspense account.
            wallet.credit(Money.of(cmd.getAmount(), cmd.getCurrency()), cmd.getDescription());
            walletRepository.save(wallet);

            compensationLog.save(buildLog(cmd.getTransactionId(), cmd.getSagaId(), "CREDIT",
                    cmd.getWalletId(), cmd.getAmount(), cmd.getCurrency()));

            sendReply(CREDIT_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "CREDIT_DEST", true, null, wallet.getAvailableBalance().getAmount());

            log.info("[WALLET-SAGA][{}] Credit OK — walletId={} newBalance={}",
                    cmd.getSagaId(), cmd.getWalletId(), wallet.getAvailableBalance().getAmount());

        } catch (Exception e) {
            log.error("[WALLET-SAGA][{}] Credit failed: {}", cmd.getSagaId(), e.getMessage(), e);
            sendReply(CREDIT_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "CREDIT_DEST", false, e.getMessage(), null);
        }

        ack.acknowledge();
    }

    // ══════════════════════════════════════════════════════════════════
    // COMPENSATION: Handle ReverseSourceDebitCommand
    // ══════════════════════════════════════════════════════════════════

    @KafkaListener(topics = REVERSE_CMD, groupId = "wallet-saga-participant-group")
    @Transactional
    public void onReverseSourceDebitCommand(@Payload ReverseDebitCmd cmd, Acknowledgment ack) {
        log.warn("[WALLET-SAGA][{}] COMPENSATION: ReverseSourceDebitCommand walletId={} amount={}",
                cmd.getSagaId(), cmd.getWalletId(), cmd.getAmount());

        // ── Idempotency check ──────────────────────────────────────
        if (compensationLog.existsByTransactionIdAndOperationType(cmd.getTransactionId(), "REVERSED")) {
            log.warn("[WALLET-SAGA][{}] Duplicate ReverseDebitCommand — already applied, re-sending OK reply",
                    cmd.getSagaId());
            sendReply(REVERSE_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "REVERSE_DEBIT", true, null, null);
            ack.acknowledge();
            return;
        }

        try {
            Wallet wallet = walletRepository.findByIdForUpdate(cmd.getWalletId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Wallet not found during compensation: " + cmd.getWalletId()));

            // Compensating transaction: credit back what was debited
            wallet.credit(
                    Money.of(cmd.getAmount(), cmd.getCurrency()),
                    "SAGA Compensation — reversal of txRef:" + cmd.getTransactionId()
            );
            walletRepository.save(wallet);

            compensationLog.save(buildLog(cmd.getTransactionId(), cmd.getSagaId(), "REVERSED",
                    cmd.getWalletId(), cmd.getAmount(), cmd.getCurrency()));

            sendReply(REVERSE_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "REVERSE_DEBIT", true, null, wallet.getAvailableBalance().getAmount());

            log.info("[WALLET-SAGA][{}] COMPENSATION OK — source wallet refunded newBalance={}",
                    cmd.getSagaId(), wallet.getAvailableBalance().getAmount());

        } catch (Exception e) {
            // 🚨 Compensation failed — coordinator will mark COMPENSATION_FAILED and alert ops
            log.error("[WALLET-SAGA][{}] 🚨 COMPENSATION FAILED: {}", cmd.getSagaId(), e.getMessage(), e);
            sendReply(REVERSE_REPLY, cmd.getSagaId(), cmd.getTransactionId(),
                    "REVERSE_DEBIT", false,
                    "Compensation failed: " + e.getMessage(), null);
            // Do NOT ack — let it retry (different from normal failure)
            return;
        }

        ack.acknowledge();
    }

    // ══════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════

    private void sendReply(String topic, UUID sagaId, UUID transactionId,
                           String stepName, boolean success,
                           String failureReason, BigDecimal newBalance) {
        CommandReply reply = CommandReply.builder()
                .sagaId(sagaId)
                .transactionId(transactionId)
                .stepName(stepName)
                .success(success)
                .failureReason(failureReason)
                .newBalance(newBalance)
                .repliedAt(LocalDateTime.now())
                .build();

        kafka.send(topic, sagaId.toString(), reply);
    }

    private SagaOperationLog buildLog(UUID transactionId, UUID sagaId,
                                      String operationType, UUID walletId,
                                      BigDecimal amount, String currency) {
        return SagaOperationLog.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .sagaId(sagaId)
                .operationType(operationType)
                .walletId(walletId)
                .amount(amount)
                .currency(currency)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── Command payload classes ────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DebitSourceCmd {
        private UUID sagaId; private UUID transactionId; private UUID walletId;
        private BigDecimal amount; private String currency; private String description;
        private LocalDateTime issuedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreditDestCmd {
        private UUID sagaId; private UUID transactionId; private UUID walletId;
        private BigDecimal amount; private String currency; private String description;
        private LocalDateTime issuedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReverseDebitCmd {
        private UUID sagaId; private UUID transactionId; private UUID walletId;
        private BigDecimal amount; private String currency;
        private String compensationReason; private LocalDateTime issuedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CommandReply {
        private UUID sagaId; private UUID transactionId; private String stepName;
        private boolean success; private String failureReason;
        private BigDecimal newBalance; private LocalDateTime repliedAt;
    }

    /** Idempotency log — records each saga operation applied to a wallet */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SagaOperationLog {
        private UUID id; private UUID transactionId; private UUID sagaId;
        private String operationType; private UUID walletId;
        private BigDecimal amount; private String currency;
        private LocalDateTime createdAt;
    }
}

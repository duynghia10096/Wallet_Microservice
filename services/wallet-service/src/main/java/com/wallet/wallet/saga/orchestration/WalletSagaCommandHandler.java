package com.wallet.wallet.saga.orchestration;

import com.wallet.wallet.domain.exception.InsufficientBalanceException;
import com.wallet.wallet.domain.model.Wallet;
import com.wallet.wallet.domain.repository.WalletRepository;
import com.wallet.wallet.domain.valueobject.Money;
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
 * ================================================================
 * ORCHESTRATION SAGA — Wallet Service Command Handler
 * ================================================================
 *
 * Wallet-service là "worker" (participant) trong orchestration saga.
 * Hoàn toàn PASSIVE — chỉ nhận lệnh từ coordinator, thực thi và reply.
 * Không biết gì về flow tổng thể hay các services khác.
 *
 * Xử lý 3 commands:
 * 1. DebitSourceCommand   → debit wallet, reply success/fail
 * 2. CreditDestCommand    → credit wallet, reply success/fail
 * 3. ReverseDebitCommand  → refund debit (compensation), reply
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletSagaCommandHandler {

    private final WalletRepository walletRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String DEBIT_CMD    = "saga.cmd.wallet.debit-source";
    private static final String CREDIT_CMD   = "saga.cmd.wallet.credit-dest";
    private static final String REVERSE_CMD  = "saga.cmd.wallet.reverse-debit";

    private static final String DEBIT_REPLY  = "saga.reply.wallet.debit";
    private static final String CREDIT_REPLY = "saga.reply.wallet.credit";
    private static final String REVERSE_REPLY= "saga.reply.wallet.reverse";

    // ─── Handle DebitSourceCommand ────────────────────────────────

    @KafkaListener(topics = DEBIT_CMD, groupId = "wallet-saga-cmd-group")
    @Transactional
    public void handleDebitSource(@Payload DebitCmd cmd, Acknowledgment ack) {
        log.info("[SAGA-ORCH][WALLET] Received DebitSourceCommand: sagaId={} walletId={} amount={}",
                cmd.getSagaId(), cmd.getWalletId(), cmd.getAmount());

        CommandReply reply;

        try {
            Wallet wallet = walletRepository.findByIdForUpdate(cmd.getWalletId())
                    .orElseThrow(() -> new RuntimeException("Wallet not found: " + cmd.getWalletId()));

            wallet.debit(Money.of(cmd.getAmount(), cmd.getCurrency()), cmd.getReason());
            walletRepository.save(wallet);

            reply = CommandReply.builder()
                    .sagaId(cmd.getSagaId())
                    .transactionId(cmd.getTransactionId())
                    .commandType("DEBIT")
                    .success(true)
                    .balanceAfter(wallet.getAvailableBalance().getAmount())
                    .repliedAt(LocalDateTime.now())
                    .build();

            log.info("[SAGA-ORCH][WALLET] Debit OK: sagaId={}", cmd.getSagaId());

        } catch (InsufficientBalanceException e) {
            reply = failReply(cmd.getSagaId(), cmd.getTransactionId(), "DEBIT",
                    "Insufficient balance");
            log.warn("[SAGA-ORCH][WALLET] Debit failed - insufficient balance: sagaId={}", cmd.getSagaId());

        } catch (Exception e) {
            reply = failReply(cmd.getSagaId(), cmd.getTransactionId(), "DEBIT", e.getMessage());
            log.error("[SAGA-ORCH][WALLET] Debit failed: {}", e.getMessage(), e);
        }

        kafkaTemplate.send(DEBIT_REPLY, cmd.getSagaId().toString(), reply);
        ack.acknowledge();
    }

    // ─── Handle CreditDestCommand ─────────────────────────────────

    @KafkaListener(topics = CREDIT_CMD, groupId = "wallet-saga-cmd-group")
    @Transactional
    public void handleCreditDest(@Payload CreditCmd cmd, Acknowledgment ack) {
        log.info("[SAGA-ORCH][WALLET] Received CreditDestCommand: sagaId={} walletId={} amount={}",
                cmd.getSagaId(), cmd.getWalletId(), cmd.getAmount());

        CommandReply reply;

        try {
            Wallet wallet = walletRepository.findByIdForUpdate(cmd.getWalletId())
                    .orElseThrow(() -> new RuntimeException("Wallet not found: " + cmd.getWalletId()));

            wallet.credit(Money.of(cmd.getAmount(), cmd.getCurrency()), cmd.getReason());
            walletRepository.save(wallet);

            reply = CommandReply.builder()
                    .sagaId(cmd.getSagaId())
                    .transactionId(cmd.getTransactionId())
                    .commandType("CREDIT")
                    .success(true)
                    .balanceAfter(wallet.getAvailableBalance().getAmount())
                    .repliedAt(LocalDateTime.now())
                    .build();

            log.info("[SAGA-ORCH][WALLET] Credit OK: sagaId={}", cmd.getSagaId());

        } catch (Exception e) {
            reply = failReply(cmd.getSagaId(), cmd.getTransactionId(), "CREDIT", e.getMessage());
            log.error("[SAGA-ORCH][WALLET] Credit failed: {}", e.getMessage(), e);
        }

        kafkaTemplate.send(CREDIT_REPLY, cmd.getSagaId().toString(), reply);
        ack.acknowledge();
    }

    // ─── Handle ReverseDebitCommand (compensation) ────────────────

    @KafkaListener(topics = REVERSE_CMD, groupId = "wallet-saga-cmd-group")
    @Transactional
    public void handleReverseDebit(@Payload ReverseCmd cmd, Acknowledgment ack) {
        log.warn("[SAGA-ORCH][WALLET] COMPENSATION ReverseDebitCommand: sagaId={} walletId={} amount={}",
                cmd.getSagaId(), cmd.getWalletId(), cmd.getAmount());

        CommandReply reply;

        try {
            Wallet wallet = walletRepository.findByIdForUpdate(cmd.getWalletId())
                    .orElseThrow(() -> new RuntimeException("Wallet not found during compensation!"));

            wallet.credit(Money.of(cmd.getAmount(), cmd.getCurrency()),
                    "SAGA Compensation Reversal");
            walletRepository.save(wallet);

            reply = CommandReply.builder()
                    .sagaId(cmd.getSagaId())
                    .transactionId(cmd.getTransactionId())
                    .commandType("REVERSE")
                    .success(true)
                    .balanceAfter(wallet.getAvailableBalance().getAmount())
                    .repliedAt(LocalDateTime.now())
                    .build();

            log.info("[SAGA-ORCH][WALLET] COMPENSATION OK: sagaId={}", cmd.getSagaId());

        } catch (Exception e) {
            // 🚨 Compensation failed - coordinator will alert ops team
            reply = failReply(cmd.getSagaId(), cmd.getTransactionId(), "REVERSE", e.getMessage());
            log.error("[SAGA-ORCH][WALLET] 🚨 COMPENSATION FAILED: sagaId={} - {}",
                    cmd.getSagaId(), e.getMessage(), e);
        }

        kafkaTemplate.send(REVERSE_REPLY, cmd.getSagaId().toString(), reply);
        ack.acknowledge();
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private CommandReply failReply(UUID sagaId, UUID transactionId, String type, String reason) {
        return CommandReply.builder()
                .sagaId(sagaId).transactionId(transactionId)
                .commandType(type).success(false)
                .failureReason(reason).repliedAt(LocalDateTime.now())
                .build();
    }

    // ─── Command / Reply payload classes ─────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DebitCmd {
        private UUID sagaId; private UUID transactionId; private UUID walletId;
        private BigDecimal amount; private String currency; private String reason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreditCmd {
        private UUID sagaId; private UUID transactionId; private UUID walletId;
        private BigDecimal amount; private String currency; private String reason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReverseCmd {
        private UUID sagaId; private UUID transactionId; private UUID walletId;
        private BigDecimal amount; private String currency; private String reason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CommandReply {
        private UUID sagaId; private UUID transactionId; private String commandType;
        private boolean success; private String failureReason;
        private BigDecimal balanceAfter; private LocalDateTime repliedAt;
    }
}

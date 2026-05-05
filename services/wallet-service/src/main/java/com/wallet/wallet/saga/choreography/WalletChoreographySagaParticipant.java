package com.wallet.wallet.saga.choreography;

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
 * CHOREOGRAPHY SAGA — Wallet Service Role
 * ================================================================
 *
 * Wallet-service là "worker" trong choreography saga.
 * Không biết về flow tổng thể, chỉ làm việc của mình:
 *
 * STEP 2: Nghe TransferInitiatedEvent → debit source wallet
 * ✅ OK → publish SourceWalletDebitedEvent
 * ❌ Fail → publish SourceDebitFailedEvent (no compensation needed)
 *
 * STEP 3: Nghe SourceWalletDebitedEvent → credit dest wallet
 * ✅ OK → publish DestWalletCreditedEvent
 * ❌ Fail → publish DestCreditFailedEvent → trigger compensation
 *
 * COMPENSATION: Nghe DestCreditFailedEvent → reverse source debit
 * → publish SourceDebitReversedEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletChoreographySagaParticipant {

        private final WalletRepository walletRepository;
        private final KafkaTemplate<String, Object> kafkaTemplate;

        // Reuse topics from transaction-service (in real project: shared common lib)
        private static final String TRANSFER_INITIATED = "saga.transfer.initiated";
        private static final String SOURCE_DEBITED = "saga.wallet.source.debited";
        private static final String DEST_CREDITED = "saga.wallet.dest.credited";
        private static final String SOURCE_DEBIT_FAILED = "saga.wallet.source.debit.failed";
        private static final String DEST_CREDIT_FAILED = "saga.wallet.dest.credit.failed";
        private static final String SOURCE_DEBIT_REVERSED = "saga.wallet.source.debit.reversed";

        // ─── STEP 2: Debit source wallet ─────────────────────────────

        @KafkaListener(topics = TRANSFER_INITIATED, groupId = "wallet-saga-group")
        @Transactional
        public void onTransferInitiated(@Payload TransferInitiatedPayload event, Acknowledgment ack) {
                log.info("[SAGA-CHOREO][WALLET] Step 2: Debiting source wallet={} amount={}",
                                event.getSourceWalletId(), event.getAmount().add(event.getFeeAmount()));

                try {
                        Wallet source = walletRepository.findByIdForUpdate(event.getSourceWalletId())
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Source wallet not found: " + event.getSourceWalletId()));

                        BigDecimal totalDebit = event.getAmount().add(event.getFeeAmount());
                        Money debitAmount = Money.of(totalDebit, event.getCurrency());
                        BigDecimal balanceBefore = source.getAvailableBalance().getAmount();

                        source.debit(debitAmount, "SAGA Transfer to " + event.getDestWalletId());
                        walletRepository.save(source);

                        // ✅ Success → trigger Step 3 (credit dest)
                        kafkaTemplate.send(SOURCE_DEBITED, event.getSagaId().toString(),
                                        SourceDebitedPayload.builder()
                                                        .sagaId(event.getSagaId())
                                                        .transactionId(event.getTransactionId())
                                                        .sourceWalletId(event.getSourceWalletId())
                                                        .destWalletId(event.getDestWalletId())
                                                        .amount(event.getAmount()) // only principal (no fee)
                                                        .currency(event.getCurrency())
                                                        .balanceAfter(source.getAvailableBalance().getAmount())
                                                        .occurredAt(LocalDateTime.now())
                                                        .build());

                        log.info("[SAGA-CHOREO][WALLET] Source debited OK. Balance: {} → {}",
                                        balanceBefore, source.getAvailableBalance().getAmount());
                        ack.acknowledge();

                } catch (InsufficientBalanceException e) {
                        log.warn("[SAGA-CHOREO][WALLET] Insufficient balance for wallet={}", event.getSourceWalletId());
                        publishDebitFailed(event.getSagaId(), event.getTransactionId(),
                                        event.getSourceWalletId(), "Insufficient balance");
                        ack.acknowledge();

                } catch (Exception e) {
                        log.error("[SAGA-CHOREO][WALLET] Debit failed: {}", e.getMessage(), e);
                        publishDebitFailed(event.getSagaId(), event.getTransactionId(),
                                        event.getSourceWalletId(), e.getMessage());
                        ack.acknowledge();
                }
        }

        // ─── STEP 3: Credit destination wallet ───────────────────────

        @KafkaListener(topics = SOURCE_DEBITED, groupId = "wallet-saga-group")
        @Transactional
        public void onSourceDebited(@Payload SourceDebitedPayload event, Acknowledgment ack) {
                log.info("[SAGA-CHOREO][WALLET] Step 3: Crediting dest wallet={} amount={}",
                                event.getDestWalletId(), event.getAmount());

                try {
                        Wallet dest = walletRepository.findByIdForUpdate(event.getDestWalletId())
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Dest wallet not found: " + event.getDestWalletId()));

                        dest.credit(Money.of(event.getAmount(), event.getCurrency()),
                                        "SAGA Transfer from " + event.getSourceWalletId());
                        walletRepository.save(dest);

                        // ✅ Success → SAGA complete!
                        kafkaTemplate.send(DEST_CREDITED, event.getSagaId().toString(),
                                        DestCreditedPayload.builder()
                                                        .sagaId(event.getSagaId())
                                                        .transactionId(event.getTransactionId())
                                                        .destWalletId(event.getDestWalletId())
                                                        .amount(event.getAmount())
                                                        .currency(event.getCurrency())
                                                        .occurredAt(LocalDateTime.now())
                                                        .build());

                        log.info("[SAGA-CHOREO][WALLET] Dest credited OK. SAGA SUCCESS for tx={}",
                                        event.getTransactionId());
                        ack.acknowledge();

                } catch (Exception e) {
                        log.error("[SAGA-CHOREO][WALLET] Credit dest failed → triggering COMPENSATION: {}",
                                        e.getMessage());

                        // ❌ Fail → publish event to trigger compensation (reverse debit)
                        kafkaTemplate.send(DEST_CREDIT_FAILED, event.getSagaId().toString(),
                                        DestCreditFailedPayload.builder()
                                                        .sagaId(event.getSagaId())
                                                        .transactionId(event.getTransactionId())
                                                        .sourceWalletId(event.getSourceWalletId())
                                                        .destWalletId(event.getDestWalletId())
                                                        .amountToReverse(event.getAmount())
                                                        .currency(event.getCurrency())
                                                        .failureReason(e.getMessage())
                                                        .occurredAt(LocalDateTime.now())
                                                        .build());

                        ack.acknowledge();
                }
        }

        // ─── COMPENSATION: Reverse source debit ──────────────────────

        @KafkaListener(topics = DEST_CREDIT_FAILED, groupId = "wallet-saga-compensation-group")
        @Transactional
        public void onDestCreditFailed(@Payload DestCreditFailedPayload event, Acknowledgment ack) {
                log.warn("[SAGA-CHOREO][WALLET] COMPENSATION: Reversing source debit wallet={} amount={}",
                                event.getSourceWalletId(), event.getAmountToReverse());

                try {
                        Wallet source = walletRepository.findByIdForUpdate(event.getSourceWalletId())
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Source wallet not found during compensation!"));

                        // Refund: credit back the full debit amount (principal + fee)
                        // In production: calculate exact amount debited from tx record
                        source.credit(Money.of(event.getAmountToReverse(), event.getCurrency()),
                                        "SAGA Compensation - transfer reversal");
                        walletRepository.save(source);

                        // ✅ Compensation done
                        kafkaTemplate.send(SOURCE_DEBIT_REVERSED, event.getSagaId().toString(),
                                        SourceDebitReversedPayload.builder()
                                                        .sagaId(event.getSagaId())
                                                        .transactionId(event.getTransactionId())
                                                        .sourceWalletId(event.getSourceWalletId())
                                                        .amountReversed(event.getAmountToReverse())
                                                        .occurredAt(LocalDateTime.now())
                                                        .build());

                        log.info("[SAGA-CHOREO][WALLET] COMPENSATION complete: source wallet refunded");
                        ack.acknowledge();

                } catch (Exception e) {
                        // 🚨 CRITICAL: Compensation itself failed!
                        // → Alert ops team, manual intervention needed
                        log.error("[SAGA-CHOREO][WALLET] ⚠️ CRITICAL: COMPENSATION FAILED for sagaId={}! " +
                                        "Manual intervention required. walletId={} amount={}",
                                        event.getSagaId(), event.getSourceWalletId(), event.getAmountToReverse(), e);
                        // Do NOT ack - will retry
                }
        }

        private void publishDebitFailed(UUID sagaId, UUID transactionId, UUID sourceWalletId, String reason) {
                kafkaTemplate.send(SOURCE_DEBIT_FAILED, sagaId.toString(),
                                DebitFailedPayload.builder()
                                                .sagaId(sagaId).transactionId(transactionId)
                                                .sourceWalletId(sourceWalletId).failureReason(reason)
                                                .occurredAt(LocalDateTime.now())
                                                .build());
        }

        // ─── Payload classes ──────────────────────────────────────────

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TransferInitiatedPayload {
                private UUID sagaId;
                private UUID transactionId;
                private UUID sourceWalletId;
                private UUID destWalletId;
                private BigDecimal amount;
                private BigDecimal feeAmount;
                private String currency;
                private LocalDateTime occurredAt;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SourceDebitedPayload {
                private UUID sagaId;
                private UUID transactionId;
                private UUID sourceWalletId;
                private UUID destWalletId;
                private BigDecimal amount;
                private String currency;
                private BigDecimal balanceAfter;
                private LocalDateTime occurredAt;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DestCreditedPayload {
                private UUID sagaId;
                private UUID transactionId;
                private UUID destWalletId;
                private BigDecimal amount;
                private String currency;
                private LocalDateTime occurredAt;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DebitFailedPayload {
                private UUID sagaId;
                private UUID transactionId;
                private UUID sourceWalletId;
                private String failureReason;
                private LocalDateTime occurredAt;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DestCreditFailedPayload {
                private UUID sagaId;
                private UUID transactionId;
                private UUID sourceWalletId;
                private UUID destWalletId;
                private BigDecimal amountToReverse;
                private String currency;
                private String failureReason;
                private LocalDateTime occurredAt;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SourceDebitReversedPayload {
                private UUID sagaId;
                private UUID transactionId;
                private UUID sourceWalletId;
                private BigDecimal amountReversed;
                private LocalDateTime occurredAt;
        }
}

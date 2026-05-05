package com.wallet.transaction.saga.orchestration.coordinator;

import com.wallet.transaction.application.dto.TransactionDto;
import com.wallet.transaction.application.dto.TransferRequest;
import com.wallet.transaction.domain.model.Transaction;
import com.wallet.transaction.domain.model.TransactionType;
import com.wallet.transaction.domain.repository.TransactionRepository;
import com.wallet.transaction.saga.orchestration.command.SagaCommands;
import com.wallet.transaction.saga.orchestration.command.SagaCommands.*;
import com.wallet.transaction.saga.state.SagaInstance;
import com.wallet.transaction.saga.state.SagaInstanceRepository;
import com.wallet.transaction.saga.state.SagaState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ================================================================
 * ORCHESTRATION SAGA — Central Coordinator (Brain)
 * ================================================================
 *
 * Coordinator biết TOÀN BỘ flow, điều phối từng bước.
 * Workers (wallet-service) chỉ làm theo lệnh, không biết về nhau.
 *
 * Advantages over Choreography:
 * ✅ Centralized state → dễ debug, monitor, query
 * ✅ Easy to add/remove steps
 * ✅ Clear compensation logic
 * ✅ Can implement timeout/retry per step
 * ✅ Saga history stored in DB
 *
 * Disadvantages:
 * ❌ Single point of failure (coordinator)
 * ❌ More complex setup
 * ❌ Coordinator becomes "god class" for complex flows
 */
@Slf4j
@Service("orchestrationTransferSagaOrchestrator")
@RequiredArgsConstructor
public class TransferSagaOrchestrator {

    private final SagaInstanceRepository sagaRepository;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ─── START SAGA ───────────────────────────────────────────────

    @Transactional
    public TransactionDto startSaga(TransferRequest request) {
        // Idempotency
        if (request.getIdempotencyKey() != null) {
            Optional<Transaction> existing = transactionRepository
                    .findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                return toDto(existing.get());
            }
        }

        BigDecimal fee = calculateFee(request.getAmount());

        // Create transaction record
        Transaction tx = Transaction.create(
                request.getSourceWalletId(), request.getDestWalletId(),
                request.getUserId(), request.getAmount(), fee,
                request.getCurrency(), TransactionType.TRANSFER,
                request.getIdempotencyKey(), request.getDescription()
        );
        transactionRepository.save(tx);

        // Create saga instance — this is our state machine checkpoint
        SagaInstance saga = SagaInstance.builder()
                .id(UUID.randomUUID())
                .transactionId(tx.getId())
                .transactionRef(tx.getTransactionRef())
                .sourceWalletId(request.getSourceWalletId())
                .destWalletId(request.getDestWalletId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .feeAmount(fee)
                .currency(request.getCurrency())
                .state(SagaState.CREATED)
                .build();
        sagaRepository.save(saga);

        // Step 1: Send COMMAND to debit source wallet
        executeStep1_DebitSource(saga);

        log.info("[SAGA-ORCH] Started sagaId={} txRef={}", saga.getId(), tx.getTransactionRef());
        return toDto(tx);
    }

    // ─── STEP 1: Send debit command ───────────────────────────────

    private void executeStep1_DebitSource(SagaInstance saga) {
        saga.transitionTo(SagaState.DEBIT_SOURCE_PENDING, "DEBIT_SOURCE");
        sagaRepository.save(saga);

        DebitSourceCommand cmd = DebitSourceCommand.builder()
                .sagaId(saga.getId())
                .transactionId(saga.getTransactionId())
                .walletId(saga.getSourceWalletId())
                .amount(saga.getAmount().add(saga.getFeeAmount()))
                .currency(saga.getCurrency())
                .reason("SAGA Transfer step 1")
                .issuedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(SagaCommands.DEBIT_SOURCE_CMD, saga.getId().toString(), cmd);
        log.info("[SAGA-ORCH] Step 1 → DebitSourceCommand sent, sagaId={}", saga.getId());
    }

    // ─── STEP 2: Send credit command after debit success ──────────

    private void executeStep2_CreditDest(SagaInstance saga) {
        saga.transitionTo(SagaState.CREDIT_DEST_PENDING, "CREDIT_DEST");
        sagaRepository.save(saga);

        CreditDestCommand cmd = CreditDestCommand.builder()
                .sagaId(saga.getId())
                .transactionId(saga.getTransactionId())
                .walletId(saga.getDestWalletId())
                .amount(saga.getAmount())   // principal only, no fee
                .currency(saga.getCurrency())
                .reason("SAGA Transfer step 2")
                .issuedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(SagaCommands.CREDIT_DEST_CMD, saga.getId().toString(), cmd);
        log.info("[SAGA-ORCH] Step 2 → CreditDestCommand sent, sagaId={}", saga.getId());
    }

    // ─── COMPENSATION: Send reverse debit command ─────────────────

    private void executeCompensation_ReverseDebit(SagaInstance saga, String reason) {
        saga.startCompensation(reason);
        sagaRepository.save(saga);

        ReverseDebitCommand cmd = ReverseDebitCommand.builder()
                .sagaId(saga.getId())
                .transactionId(saga.getTransactionId())
                .walletId(saga.getSourceWalletId())
                .amount(saga.getAmount().add(saga.getFeeAmount()))
                .currency(saga.getCurrency())
                .reason("SAGA Compensation: " + reason)
                .issuedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(SagaCommands.REVERSE_DEBIT_CMD, saga.getId().toString(), cmd);
        log.warn("[SAGA-ORCH] COMPENSATION → ReverseDebitCommand sent, sagaId={}", saga.getId());
    }

    // ─── HANDLE REPLIES from wallet-service ──────────────────────

    @KafkaListener(topics = SagaCommands.DEBIT_REPLY, groupId = "saga-orchestrator-group")
    @Transactional
    public void onDebitReply(@Payload CommandReply reply, Acknowledgment ack) {
        log.info("[SAGA-ORCH] Debit reply: sagaId={} success={}", reply.getSagaId(), reply.isSuccess());

        SagaInstance saga = sagaRepository.findByIdWithLock(reply.getSagaId())
                .orElseThrow(() -> new RuntimeException("Saga not found: " + reply.getSagaId()));

        if (saga.getState().isTerminal()) {
            log.warn("[SAGA-ORCH] Saga already terminal ({}), ignoring debit reply", saga.getState());
            ack.acknowledge();
            return;
        }

        if (saga.getState() != SagaState.DEBIT_SOURCE_PENDING) {
            log.warn("[SAGA-ORCH] Unexpected reply in state {}, ignoring", saga.getState());
            ack.acknowledge();
            return;
        }

        if (reply.isSuccess()) {
            // → Move to Step 2
            executeStep2_CreditDest(saga);
        } else {
            // → Fail immediately (nothing to compensate, debit didn't happen)
            saga.markFailed("Source debit failed: " + reply.getFailureReason());
            sagaRepository.save(saga);
            markTransactionFailed(saga.getTransactionId(), reply.getFailureReason());
            log.warn("[SAGA-ORCH] Saga FAILED at step 1, sagaId={}", saga.getId());
        }

        ack.acknowledge();
    }

    @KafkaListener(topics = SagaCommands.CREDIT_REPLY, groupId = "saga-orchestrator-group")
    @Transactional
    public void onCreditReply(@Payload CommandReply reply, Acknowledgment ack) {
        log.info("[SAGA-ORCH] Credit reply: sagaId={} success={}", reply.getSagaId(), reply.isSuccess());

        SagaInstance saga = sagaRepository.findByIdWithLock(reply.getSagaId())
                .orElseThrow(() -> new RuntimeException("Saga not found: " + reply.getSagaId()));

        if (saga.getState().isTerminal()) {
            log.warn("[SAGA-ORCH] Saga already terminal ({}), ignoring credit reply", saga.getState());
            ack.acknowledge();
            return;
        }

        if (saga.getState() != SagaState.CREDIT_DEST_PENDING) {
            log.warn("[SAGA-ORCH] Unexpected reply in state {}, ignoring", saga.getState());
            ack.acknowledge();
            return;
        }

        if (reply.isSuccess()) {
            // → SAGA COMPLETE ✅
            saga.markCompleted();
            sagaRepository.save(saga);
            markTransactionCompleted(saga.getTransactionId());
            log.info("[SAGA-ORCH] ✅ Saga COMPLETED, sagaId={}", saga.getId());
        } else {
            // → Credit failed, trigger COMPENSATION
            executeCompensation_ReverseDebit(saga, reply.getFailureReason());
        }

        ack.acknowledge();
    }

    @KafkaListener(topics = SagaCommands.REVERSE_REPLY, groupId = "saga-orchestrator-group")
    @Transactional
    public void onReverseReply(@Payload CommandReply reply, Acknowledgment ack) {
        log.info("[SAGA-ORCH] Reverse reply: sagaId={} success={}", reply.getSagaId(), reply.isSuccess());

        SagaInstance saga = sagaRepository.findByIdWithLock(reply.getSagaId())
                .orElseThrow(() -> new RuntimeException("Saga not found: " + reply.getSagaId()));

        if (saga.getState().isTerminal()) {
            log.warn("[SAGA-ORCH] Saga already terminal ({}), ignoring reverse reply", saga.getState());
            ack.acknowledge();
            return;
        }

        if (saga.getState() != SagaState.REVERSING_SOURCE_DEBIT) {
            log.warn("[SAGA-ORCH] Unexpected reverse reply in state {}, ignoring", saga.getState());
            ack.acknowledge();
            return;
        }

        if (reply.isSuccess()) {
            // → Compensation done, money returned ❌ (but clean)
            saga.markCompensated();
            sagaRepository.save(saga);
            markTransactionFailed(saga.getTransactionId(),
                    "Transaction failed - " + saga.getFailureReason() + ". Amount refunded.");
            log.warn("[SAGA-ORCH] Saga COMPENSATED (money returned), sagaId={}", saga.getId());
        } else {
            // → 🚨 CRITICAL: Compensation failed!
            saga.markCompensationFailed(reply.getFailureReason());
            sagaRepository.save(saga);
            log.error("[SAGA-ORCH] 🚨 COMPENSATION FAILED! sagaId={} - MANUAL INTERVENTION NEEDED",
                    saga.getId());
            // Alert ops team via PagerDuty / Slack / email
        }

        ack.acknowledge();
    }

    // ─── RECOVERY JOB: Resume stuck sagas ─────────────────────────

    /**
     * Runs every 5 minutes. Finds sagas stuck > 10 minutes and retries.
     * Handles cases where coordinator crashed mid-saga.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void recoverStuckSagas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<SagaInstance> stuckSagas = sagaRepository.findStuckSagas(threshold);

        if (!stuckSagas.isEmpty()) {
            log.warn("[SAGA-ORCH] Found {} stuck sagas, recovering...", stuckSagas.size());
        }

        for (SagaInstance saga : stuckSagas) {
            if (saga.getState().isTerminal()) {
                continue;
            }

            if (saga.getRetryCount() >= 3) {
                log.error("[SAGA-ORCH] Saga exceeded max retries, marking FAILED: {}", saga.getId());
                saga.markFailed("Max retries exceeded");
                sagaRepository.save(saga);
                markTransactionFailed(saga.getTransactionId(), "Saga timeout: max retries exceeded");
                continue;
            }

            saga.incrementRetry();
            log.warn("[SAGA-ORCH] Retrying stuck saga: {} state={} retry={}",
                    saga.getId(), saga.getState(), saga.getRetryCount());

            // Re-send command based on current state
            switch (saga.getState()) {
                case DEBIT_SOURCE_PENDING -> executeStep1_DebitSource(saga);
                case CREDIT_DEST_PENDING  -> executeStep2_CreditDest(saga);
                case REVERSING_SOURCE_DEBIT -> executeCompensation_ReverseDebit(saga, "Recovery retry");
                default -> log.warn("[SAGA-ORCH] Unknown stuck state: {}", saga.getState());
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private void markTransactionCompleted(UUID transactionId) {
        transactionRepository.findById(transactionId).ifPresent(tx -> {
            tx.complete();
            transactionRepository.save(tx);
        });
    }

    private void markTransactionFailed(UUID transactionId, String reason) {
        transactionRepository.findById(transactionId).ifPresent(tx -> {
            tx.fail(reason);
            transactionRepository.save(tx);
        });
    }

    private BigDecimal calculateFee(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(0.001))
                .max(BigDecimal.valueOf(1000))
                .min(BigDecimal.valueOf(50000));
    }

    private TransactionDto toDto(Transaction t) {
        return TransactionDto.builder()
                .id(t.getId()).transactionRef(t.getTransactionRef())
                .type(t.getType().name()).status(t.getStatus().name())
                .sourceWalletId(t.getSourceWalletId()).destWalletId(t.getDestWalletId())
                .amount(t.getAmount()).feeAmount(t.getFeeAmount())
                .totalAmount(t.getAmount().add(t.getFeeAmount()))
                .currency(t.getCurrency()).description(t.getDescription())
                .initiatedAt(t.getInitiatedAt()).completedAt(t.getCompletedAt())
                .failureReason(t.getFailureReason())
                .build();
    }
}


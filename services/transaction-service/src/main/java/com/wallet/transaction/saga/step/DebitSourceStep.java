package com.wallet.transaction.saga.step;

import com.wallet.transaction.saga.command.SagaCommand;
import com.wallet.transaction.saga.state.SagaInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Step 1: Debit the source wallet.
 *
 * execute()    → send DebitSourceCommand
 * compensate() → send ReverseSourceDebitCommand (if a later step fails)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebitSourceStep implements SagaStep {

    private final KafkaTemplate<String, Object> kafka;

    @Override
    public String stepName() {
        return "DEBIT_SOURCE";
    }

    @Override
    public void execute(SagaInstance saga) {
        SagaCommand.DebitSourceCommand cmd = SagaCommand.DebitSourceCommand.builder()
                .sagaId(saga.getId())
                .transactionId(saga.getTransactionId())
                .walletId(saga.getSourceWalletId())
                .amount(saga.totalDebitAmount())     // principal + fee
                .currency(saga.getCurrency())
                .description("Transfer debit - ref:" + saga.getTransactionRef())
                .issuedAt(LocalDateTime.now())
                .build();

        kafka.send(SagaCommand.DEBIT_SOURCE_CMD, saga.getId().toString(), cmd);

        log.info("[SAGA][{}] Step 1 SENT DebitSourceCommand → walletId={} amount={}",
                saga.getId(), saga.getSourceWalletId(), saga.totalDebitAmount());
    }

    @Override
    public boolean hasCompensation() {
        return true;
    }

    /**
     * Compensation: refund (amount + fee) back to source wallet.
     * Called when CreditDestStep fails after this step already succeeded.
     */
    @Override
    public void compensate(SagaInstance saga) {
        SagaCommand.ReverseSourceDebitCommand cmd = SagaCommand.ReverseSourceDebitCommand.builder()
                .sagaId(saga.getId())
                .transactionId(saga.getTransactionId())   // idempotency key in wallet-service
                .walletId(saga.getSourceWalletId())
                .amount(saga.totalDebitAmount())
                .currency(saga.getCurrency())
                .compensationReason(saga.getFailureReason())
                .issuedAt(LocalDateTime.now())
                .build();

        kafka.send(SagaCommand.REVERSE_DEBIT_CMD, saga.getId().toString(), cmd);

        log.warn("[SAGA][{}] COMPENSATION SENT ReverseSourceDebitCommand → walletId={} amount={}",
                saga.getId(), saga.getSourceWalletId(), saga.totalDebitAmount());
    }
}

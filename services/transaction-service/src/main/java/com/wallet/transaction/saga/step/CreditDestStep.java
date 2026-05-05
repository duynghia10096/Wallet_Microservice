package com.wallet.transaction.saga.step;

import com.wallet.transaction.saga.command.SagaCommand;
import com.wallet.transaction.saga.state.SagaInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Step 2: Credit the destination wallet.
 *
 * execute()    → send CreditDestCommand
 * compensate() → N/A. If this step fails, coordinator compensates Step 1.
 *
 * Why no compensation for credit?
 * ─ If credit fails, nothing was credited → nothing to undo here.
 * ─ The compensation is on Step 1: reverse the source debit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditDestStep implements SagaStep {

    private final KafkaTemplate<String, Object> kafka;

    @Override
    public String stepName() {
        return "CREDIT_DEST";
    }

    @Override
    public void execute(SagaInstance saga) {
        SagaCommand.CreditDestCommand cmd = SagaCommand.CreditDestCommand.builder()
                .sagaId(saga.getId())
                .transactionId(saga.getTransactionId())
                .walletId(saga.getDestWalletId())
                .amount(saga.getAmount())     // principal only — fee stays in platform
                .currency(saga.getCurrency())
                .description("Transfer credit - ref:" + saga.getTransactionRef())
                .issuedAt(LocalDateTime.now())
                .build();

        kafka.send(SagaCommand.CREDIT_DEST_CMD, saga.getId().toString(), cmd);

        log.info("[SAGA][{}] Step 2 SENT CreditDestCommand → walletId={} amount={}",
                saga.getId(), saga.getDestWalletId(), saga.getAmount());
    }

    @Override
    public boolean hasCompensation() {
        return false; // failure here triggers Step 1's compensation
    }
}

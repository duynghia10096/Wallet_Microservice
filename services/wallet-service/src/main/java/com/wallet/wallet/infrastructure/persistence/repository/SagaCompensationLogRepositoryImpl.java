package com.wallet.wallet.infrastructure.persistence.repository;

import com.wallet.wallet.infrastructure.persistence.entity.SagaOperationLogEntity;
import com.wallet.wallet.saga.participant.WalletSagaParticipant.SagaOperationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SagaCompensationLogRepositoryImpl implements SagaCompensationLogRepository {

    private final SagaOperationLogJpaRepository jpa;

    @Override
    public void save(SagaOperationLog log) {
        jpa.save(SagaOperationLogEntity.builder()
                .id(log.getId())
                .transactionId(log.getTransactionId())
                .sagaId(log.getSagaId())
                .operationType(log.getOperationType())
                .walletId(log.getWalletId())
                .amount(log.getAmount())
                .currency(log.getCurrency())
                .build());
    }

    @Override
    public boolean existsByTransactionIdAndOperationType(UUID transactionId, String operationType) {
        return jpa.existsByTransactionIdAndOperationType(transactionId, operationType);
    }
}

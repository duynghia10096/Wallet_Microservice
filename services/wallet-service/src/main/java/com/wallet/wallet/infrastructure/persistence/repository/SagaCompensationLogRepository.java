package com.wallet.wallet.infrastructure.persistence.repository;

import com.wallet.wallet.saga.participant.WalletSagaParticipant.SagaOperationLog;

import java.util.UUID;

/**
 * Port (domain-facing interface) for idempotency log.
 * WalletSagaParticipant depends on this, not on JPA directly.
 */
public interface SagaCompensationLogRepository {
    void save(SagaOperationLog log);
    boolean existsByTransactionIdAndOperationType(UUID transactionId, String operationType);
}

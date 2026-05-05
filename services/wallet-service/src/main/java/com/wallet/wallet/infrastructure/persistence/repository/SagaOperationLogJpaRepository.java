package com.wallet.wallet.infrastructure.persistence.repository;

import com.wallet.wallet.infrastructure.persistence.entity.SagaOperationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SagaOperationLogJpaRepository extends JpaRepository<SagaOperationLogEntity, UUID> {
    boolean existsByTransactionIdAndOperationType(UUID transactionId, String operationType);
}

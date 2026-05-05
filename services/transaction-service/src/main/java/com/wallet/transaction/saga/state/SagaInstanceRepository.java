package com.wallet.transaction.saga.state;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

    Optional<SagaInstance> findByTransactionId(UUID transactionId);

    /**
     * Pessimistic write lock — used by coordinator to prevent
     * concurrent reply processing for the same saga.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SagaInstance s WHERE s.id = :id")
    Optional<SagaInstance> findByIdWithLock(@Param("id") UUID id);

    /**
     * Recovery job: find sagas that haven't progressed for > threshold.
     * Excludes terminal states.
     */
    @Query("""
        SELECT s FROM SagaInstance s
        WHERE s.state NOT IN (
            com.wallet.transaction.saga.state.SagaState.COMPLETED,
            com.wallet.transaction.saga.state.SagaState.COMPENSATED,
            com.wallet.transaction.saga.state.SagaState.FAILED,
            com.wallet.transaction.saga.state.SagaState.COMPENSATION_FAILED,
            com.wallet.transaction.saga.state.SagaState.TIMED_OUT
        )
        AND s.lastStepAt < :threshold
        ORDER BY s.lastStepAt ASC
        """)
    List<SagaInstance> findStuckSagas(@Param("threshold") LocalDateTime threshold);

    /** Ops dashboard: sagas needing manual intervention */
    List<SagaInstance> findByState(SagaState state);

    /** Metrics: count by state */
    @Query("SELECT s.state, COUNT(s) FROM SagaInstance s GROUP BY s.state")
    List<Object[]> countByState();

    /** Audit: all sagas for a user */
    List<SagaInstance> findByUserIdOrderByCreatedAtDesc(UUID userId);
}

package com.wallet.transaction.domain.repository;

import com.wallet.transaction.domain.model.Transaction;
import com.wallet.transaction.domain.model.TransactionStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(String key);

    Page<Transaction> findBySourceWalletIdOrDestWalletId(UUID sourceWalletId, UUID destWalletId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount + t.feeAmount), 0) " +
            "FROM Transaction t " +
            "WHERE t.sourceWalletId = :walletId " +
            "AND t.currency = :currency " +
            "AND t.initiatedAt >= :from " +
            "AND t.status = :status")
    BigDecimal sumDailyDebits(@Param("walletId") UUID walletId,
                               @Param("currency") String currency,
                               @Param("from") LocalDateTime from,
                               @Param("status") TransactionStatus status);
}

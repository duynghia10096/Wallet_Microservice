package com.wallet.transaction.application.service;

import com.wallet.common.event.TransactionCompletedEvent;
import com.wallet.common.event.TransactionFailedEvent;
import com.wallet.transaction.application.dto.*;
import com.wallet.transaction.domain.model.*;
import com.wallet.transaction.domain.repository.TransactionRepository;
import com.wallet.transaction.infrastructure.client.*;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionApplicationService {

    private final TransactionRepository transactionRepository;
    private final WalletServiceClient walletClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Execute a transfer between wallets.
     *
     * Communication pattern:
     * 1. Transaction Service calls Wallet Service via Feign (sync REST)
     *    to debit source and credit destination
     * 2. On success: publish TransactionCompletedEvent to Kafka (async)
     * 3. Notification Service listens and sends push/email notification
     *
     * Failure handling:
     * - If debit succeeds but credit fails → compensating transaction (reverse debit)
     * - This is the SAGA pattern (choreography-based)
     */
    @Transactional
    public TransactionDto transfer(TransferRequest request) {
        // 1. Idempotency check
        if (request.getIdempotencyKey() != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Duplicate transfer, returning existing: {}", request.getIdempotencyKey());
                return toDto(existing.get());
            }
        }

        // 2. Validate wallets exist
        WalletResponse sourceWallet = fetchWalletOrThrow(request.getSourceWalletId(), "source");
        WalletResponse destWallet = fetchWalletOrThrow(request.getDestWalletId(), "destination");

        if (!"ACTIVE".equals(sourceWallet.getStatus())) {
            throw new com.wallet.common.exception.ServiceException(
                    "Source wallet is not active", "WALLET_NOT_ACTIVE",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        }

        BigDecimal amount = request.getAmount();
        BigDecimal fee = calculateFee(amount);
        BigDecimal totalDebit = amount.add(fee);

        // 3. Create transaction record
        Transaction transaction = Transaction.create(
                request.getSourceWalletId(),
                request.getDestWalletId(),
                request.getUserId(),
                amount, fee,
                request.getCurrency(),
                TransactionType.TRANSFER,
                request.getIdempotencyKey(),
                request.getDescription()
        );
        transactionRepository.save(transaction);

        // 4. Execute: Debit source wallet (Feign call to wallet-service)
        try {
            walletClient.debit(request.getSourceWalletId(),
                    new BalanceChangeRequest(totalDebit, request.getCurrency(),
                            "Transfer to " + destWallet.getWalletNumber()));
        } catch (Exception e) {
            transaction.fail("Failed to debit source wallet: " + e.getMessage());
            transactionRepository.save(transaction);
            publishFailedEvent(transaction, request.getUserId());
            throw e;
        }

        // 5. Execute: Credit destination wallet (Feign call to wallet-service)
        try {
            walletClient.credit(request.getDestWalletId(),
                    new BalanceChangeRequest(amount, request.getCurrency(),
                            "Transfer from " + sourceWallet.getWalletNumber()));
        } catch (Exception e) {
            // COMPENSATING TRANSACTION: reverse the debit
            log.error("Credit failed, compensating by reversing debit for transaction: {}", transaction.getId());
            try {
                walletClient.credit(request.getSourceWalletId(),
                        new BalanceChangeRequest(totalDebit, request.getCurrency(), "Reversal"));
            } catch (Exception compensationEx) {
                log.error("CRITICAL: Compensation failed! Manual intervention needed for tx: {}",
                        transaction.getId(), compensationEx);
            }
            transaction.fail("Failed to credit destination wallet: " + e.getMessage());
            transactionRepository.save(transaction);
            publishFailedEvent(transaction, request.getUserId());
            throw e;
        }

        // 6. Mark complete and publish event
        transaction.complete();
        transactionRepository.save(transaction);

        kafkaTemplate.send(TransactionCompletedEvent.TOPIC, transaction.getId().toString(),
                TransactionCompletedEvent.builder()
                        .transactionId(transaction.getId())
                        .transactionRef(transaction.getTransactionRef())
                        .transactionType("TRANSFER")
                        .sourceWalletId(request.getSourceWalletId())
                        .destWalletId(request.getDestWalletId())
                        .userId(request.getUserId())
                        .amount(amount)
                        .feeAmount(fee)
                        .currency(request.getCurrency())
                        .occurredAt(LocalDateTime.now())
                        .build());

        log.info("Transfer completed: ref={}", transaction.getTransactionRef());
        return toDto(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionDto> getWalletTransactions(UUID walletId, Pageable pageable) {
        return transactionRepository
                .findBySourceWalletIdOrDestWalletId(walletId, walletId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .map(this::toDto)
                .orElseThrow(() -> new com.wallet.common.exception.ServiceException(
                        "Transaction not found: " + transactionId,
                        "TRANSACTION_NOT_FOUND",
                        org.springframework.http.HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public BigDecimal getDailyTotal(UUID walletId, String currency) {
        return transactionRepository.sumDailyDebits(
                walletId,
                currency,
                LocalDateTime.now().toLocalDate().atStartOfDay(),
                TransactionStatus.COMPLETED);
    }

    private BigDecimal calculateFee(BigDecimal amount) {
        BigDecimal fee = amount.multiply(BigDecimal.valueOf(0.001));
        return fee.max(BigDecimal.valueOf(1000)).min(BigDecimal.valueOf(50000));
    }

    private WalletResponse fetchWalletOrThrow(UUID walletId, String walletRole) {
        try {
            return walletClient.getWallet(walletId);
        } catch (FeignException.NotFound ex) {
            throw new com.wallet.common.exception.ServiceException(
                    String.format("%s wallet not found: %s", walletRole, walletId),
                    "WALLET_NOT_FOUND",
                    HttpStatus.NOT_FOUND);
        } catch (FeignException ex) {
            throw new com.wallet.common.exception.ServiceException(
                    "Failed to fetch wallet information",
                    "WALLET_SERVICE_ERROR",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private void publishFailedEvent(Transaction tx, UUID userId) {
        kafkaTemplate.send(TransactionFailedEvent.TOPIC, tx.getId().toString(),
                TransactionFailedEvent.builder()
                        .transactionId(tx.getId())
                        .transactionRef(tx.getTransactionRef())
                        .userId(userId)
                        .amount(tx.getAmount())
                        .currency(tx.getCurrency())
                        .failureReason(tx.getFailureReason())
                        .occurredAt(LocalDateTime.now())
                        .build());
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

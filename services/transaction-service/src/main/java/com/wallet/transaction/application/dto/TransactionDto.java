package com.wallet.transaction.application.dto;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class TransactionDto {
    UUID id;
    String transactionRef;
    String type;
    String status;
    UUID sourceWalletId;
    UUID destWalletId;
    BigDecimal amount;
    BigDecimal feeAmount;
    BigDecimal totalAmount;
    String currency;
    String description;
    LocalDateTime initiatedAt;
    LocalDateTime completedAt;
    String failureReason;
}

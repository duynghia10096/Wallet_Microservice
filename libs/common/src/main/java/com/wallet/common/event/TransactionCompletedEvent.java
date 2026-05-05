package com.wallet.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCompletedEvent {
    public static final String TOPIC = "transaction.completed";
    private UUID transactionId;
    private String transactionRef;
    private String transactionType;
    private UUID sourceWalletId;
    private UUID destWalletId;
    private UUID userId;
    private BigDecimal amount;
    private BigDecimal feeAmount;
    private String currency;
    private LocalDateTime occurredAt;
}

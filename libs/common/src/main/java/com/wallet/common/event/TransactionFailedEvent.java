package com.wallet.common.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFailedEvent {
    public static final String TOPIC = "transaction.failed";
    private UUID transactionId;
    private String transactionRef;
    private String transactionType;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private String failureReason;
    private LocalDateTime occurredAt;
}

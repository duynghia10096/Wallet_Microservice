package com.wallet.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreatedEvent {
    public static final String TOPIC = "wallet.created";
    private UUID walletId;
    private UUID userId;
    private String walletNumber;
    private String walletType;
    private String currency;
    private LocalDateTime occurredAt;
}

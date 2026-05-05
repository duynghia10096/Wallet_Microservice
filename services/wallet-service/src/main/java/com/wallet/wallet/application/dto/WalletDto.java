package com.wallet.wallet.application.dto;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class WalletDto {
    UUID id;
    String walletNumber;
    UUID userId;
    String walletType;
    String currency;
    BigDecimal balance;
    BigDecimal availableBalance;
    BigDecimal frozenBalance;
    String status;
    LocalDateTime createdAt;
}

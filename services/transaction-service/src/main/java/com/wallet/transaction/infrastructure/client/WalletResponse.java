package com.wallet.transaction.infrastructure.client;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class WalletResponse {
    private UUID id;
    private String walletNumber;
    private UUID userId;
    private String walletType;
    private String currency;
    private BigDecimal availableBalance;
    private String status;
}

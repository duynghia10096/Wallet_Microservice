package com.wallet.transaction.infrastructure.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceChangeRequest {
    private BigDecimal amount;
    private String currency;
    private String reason;
}

package com.wallet.transaction.application.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequest {
    @NotNull
    private UUID sourceWalletId;
    @NotNull
    private UUID destWalletId;
    @NotNull
    private UUID userId;
    @NotNull
    @Positive
    private BigDecimal amount;
    @NotBlank
    private String currency;
    private String description;
    private String idempotencyKey;
}

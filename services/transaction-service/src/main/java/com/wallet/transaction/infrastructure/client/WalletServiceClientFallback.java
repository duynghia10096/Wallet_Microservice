package com.wallet.transaction.infrastructure.client;

import com.wallet.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Slf4j
@Component
public class WalletServiceClientFallback implements WalletServiceClient {
    @Override
    public WalletResponse getWallet(UUID walletId) {
        log.error("Wallet service unavailable for getWallet: {}", walletId);
        throw new ServiceException("Wallet service unavailable", "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public WalletResponse credit(UUID walletId, BalanceChangeRequest request) {
        log.error("Wallet service unavailable for credit: {}", walletId);
        throw new ServiceException("Wallet service unavailable", "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public WalletResponse debit(UUID walletId, BalanceChangeRequest request) {
        log.error("Wallet service unavailable for debit: {}", walletId);
        throw new ServiceException("Wallet service unavailable", "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }
}

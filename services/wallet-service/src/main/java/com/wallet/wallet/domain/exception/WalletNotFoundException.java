package com.wallet.wallet.domain.exception;
import com.wallet.common.exception.ServiceException;
import org.springframework.http.HttpStatus;
public class WalletNotFoundException extends ServiceException {
    public WalletNotFoundException(String id) {
        super("Wallet not found: " + id, "WALLET_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}

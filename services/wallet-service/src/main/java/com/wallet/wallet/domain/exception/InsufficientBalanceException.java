package com.wallet.wallet.domain.exception;
import com.wallet.common.exception.ServiceException;
import org.springframework.http.HttpStatus;
public class InsufficientBalanceException extends ServiceException {
    public InsufficientBalanceException(String walletId) {
        super("Insufficient balance in wallet: " + walletId, "INSUFFICIENT_BALANCE", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

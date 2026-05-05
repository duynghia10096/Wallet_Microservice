package com.wallet.user.domain.exception;
import com.wallet.common.exception.ServiceException;
import org.springframework.http.HttpStatus;
public class InvalidCredentialsException extends ServiceException {
    public InvalidCredentialsException() {
        super("Invalid email or password", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
    }
}

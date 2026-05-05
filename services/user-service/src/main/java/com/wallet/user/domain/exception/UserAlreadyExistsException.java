package com.wallet.user.domain.exception;
import com.wallet.common.exception.ServiceException;
import org.springframework.http.HttpStatus;
public class UserAlreadyExistsException extends ServiceException {
    public UserAlreadyExistsException(String field) {
        super("User already exists with " + field, "USER_ALREADY_EXISTS", HttpStatus.CONFLICT);
    }
}

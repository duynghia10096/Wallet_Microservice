package com.wallet.user.domain.exception;
import com.wallet.common.exception.ServiceException;
import org.springframework.http.HttpStatus;
public class UserNotFoundException extends ServiceException {
    public UserNotFoundException(String id) {
        super("User not found: " + id, "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}

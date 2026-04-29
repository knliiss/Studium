package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends AppException {
    
    public UserNotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User not found"
        );
    }
}
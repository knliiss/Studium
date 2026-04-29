package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class PasswordResetTokenNotFoundException extends AppException {
    
    public PasswordResetTokenNotFoundException() {
        super(
                HttpStatus.UNAUTHORIZED,
                "PASSWORD_RESET_TOKEN_NOT_FOUND",
                "Password reset token not found"
        );
    }
}
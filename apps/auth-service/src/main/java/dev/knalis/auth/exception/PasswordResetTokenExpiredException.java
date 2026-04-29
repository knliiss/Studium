package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class PasswordResetTokenExpiredException extends AppException {
    
    public PasswordResetTokenExpiredException() {
        super(
                HttpStatus.UNAUTHORIZED,
                "PASSWORD_RESET_TOKEN_EXPIRED",
                "Password reset token is expired, revoked, or already used"
        );
    }
}
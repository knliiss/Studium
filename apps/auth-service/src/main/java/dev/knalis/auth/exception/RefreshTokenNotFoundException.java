package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class RefreshTokenNotFoundException extends AppException {
    
    public RefreshTokenNotFoundException() {
        super(
                HttpStatus.UNAUTHORIZED,
                "REFRESH_TOKEN_NOT_FOUND",
                "Refresh token not found"
        );
    }
}
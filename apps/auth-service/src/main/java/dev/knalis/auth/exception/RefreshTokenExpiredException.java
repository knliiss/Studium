package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class RefreshTokenExpiredException extends AppException {
    
    public RefreshTokenExpiredException() {
        super(
                HttpStatus.UNAUTHORIZED,
                "REFRESH_TOKEN_EXPIRED",
                "Refresh token is expired or revoked"
        );
    }
}
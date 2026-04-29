package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

public class UserBannedException extends AppException {
    
    public UserBannedException() {
        super(
                HttpStatus.FORBIDDEN,
                "USER_BANNED",
                "User is permanently banned"
        );
    }
    
    public UserBannedException(Instant expiresAt) {
        super(
                HttpStatus.FORBIDDEN,
                "USER_BANNED",
                "User is banned until " + expiresAt,
                Map.of("expiresAt", expiresAt.toString())
        );
    }
}
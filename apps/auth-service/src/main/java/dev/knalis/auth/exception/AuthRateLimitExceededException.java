package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class AuthRateLimitExceededException extends AppException {

    public AuthRateLimitExceededException(String scope, long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Try again later.",
                Map.of(
                        "scope", scope,
                        "retryAfterSeconds", retryAfterSeconds
                )
        );
    }
}

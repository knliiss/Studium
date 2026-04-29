package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class MfaChallengeExpiredException extends AppException {
    
    public MfaChallengeExpiredException() {
        super(HttpStatus.UNAUTHORIZED, "MFA_CHALLENGE_EXPIRED", "MFA challenge is expired");
    }
}

package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class MfaChallengeResolvedException extends AppException {
    
    public MfaChallengeResolvedException() {
        super(HttpStatus.CONFLICT, "MFA_CHALLENGE_RESOLVED", "MFA challenge is already resolved");
    }
}

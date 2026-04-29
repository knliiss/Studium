package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class MfaChallengeNotFoundException extends AppException {
    
    public MfaChallengeNotFoundException() {
        super(HttpStatus.NOT_FOUND, "MFA_CHALLENGE_NOT_FOUND", "MFA challenge not found");
    }
}

package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class MfaVerificationFailedException extends AppException {
    
    public MfaVerificationFailedException() {
        super(HttpStatus.UNAUTHORIZED, "MFA_VERIFICATION_FAILED", "MFA code is invalid or expired");
    }
}

package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class MfaDispatchLimitExceededException extends AppException {
    
    public MfaDispatchLimitExceededException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "MFA_DISPATCH_LIMIT_EXCEEDED", "MFA code dispatch limit exceeded");
    }
}

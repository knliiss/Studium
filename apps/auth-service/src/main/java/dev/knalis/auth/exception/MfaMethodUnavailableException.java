package dev.knalis.auth.exception;

import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class MfaMethodUnavailableException extends AppException {
    
    public MfaMethodUnavailableException(MfaMethodType methodType) {
        super(
                HttpStatus.BAD_REQUEST,
                "MFA_METHOD_UNAVAILABLE",
                "MFA method is not available for this user",
                Map.of("method", methodType.name())
        );
    }
}

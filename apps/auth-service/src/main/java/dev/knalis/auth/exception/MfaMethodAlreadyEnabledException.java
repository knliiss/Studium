package dev.knalis.auth.exception;

import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class MfaMethodAlreadyEnabledException extends AppException {
    
    public MfaMethodAlreadyEnabledException(MfaMethodType methodType) {
        super(
                HttpStatus.CONFLICT,
                "MFA_METHOD_ALREADY_ENABLED",
                "MFA method is already enabled",
                Map.of("method", methodType.name())
        );
    }
}

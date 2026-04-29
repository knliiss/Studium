package dev.knalis.auth.exception;

import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class MfaMethodNotConfiguredException extends AppException {
    
    public MfaMethodNotConfiguredException(MfaMethodType methodType) {
        super(
                HttpStatus.BAD_REQUEST,
                "MFA_METHOD_NOT_CONFIGURED",
                "MFA method is not configured",
                Map.of("method", methodType.name())
        );
    }
}

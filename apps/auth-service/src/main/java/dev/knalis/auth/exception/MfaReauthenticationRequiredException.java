package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class MfaReauthenticationRequiredException extends AppException {

    public MfaReauthenticationRequiredException() {
        super(
                HttpStatus.UNAUTHORIZED,
                "MFA_REAUTH_REQUIRED",
                "Re-authentication with MFA is required"
        );
    }
}

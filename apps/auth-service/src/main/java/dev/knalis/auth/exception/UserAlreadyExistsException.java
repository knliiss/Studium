package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class UserAlreadyExistsException extends AppException {
    
    public UserAlreadyExistsException(String field, String value) {
        super(
                HttpStatus.CONFLICT,
                "USER_ALREADY_EXISTS",
                "User with this " + field + " already exists",
                Map.of(
                        "field", field,
                        "value", value
                )
        );
    }
}
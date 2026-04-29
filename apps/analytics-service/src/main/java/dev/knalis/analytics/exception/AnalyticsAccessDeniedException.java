package dev.knalis.analytics.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AnalyticsAccessDeniedException extends AppException {
    
    public AnalyticsAccessDeniedException(UUID targetUserId) {
        super(
                HttpStatus.FORBIDDEN,
                "ANALYTICS_ACCESS_DENIED",
                "Analytics access is denied",
                Map.of("targetUserId", targetUserId)
        );
    }
}

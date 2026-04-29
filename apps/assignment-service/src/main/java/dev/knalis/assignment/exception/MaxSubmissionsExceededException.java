package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class MaxSubmissionsExceededException extends AppException {

    public MaxSubmissionsExceededException(UUID assignmentId, UUID userId, int maxSubmissions) {
        super(
                HttpStatus.CONFLICT,
                "MAX_SUBMISSIONS_EXCEEDED",
                "Maximum number of submissions for this assignment has been reached",
                Map.of(
                        "assignmentId", assignmentId,
                        "userId", userId,
                        "maxSubmissions", maxSubmissions
                )
        );
    }
}

package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class DeadlineExpiredException extends AppException {

    public DeadlineExpiredException(UUID assignmentId, Instant deadline) {
        super(
                HttpStatus.CONFLICT,
                "DEADLINE_EXPIRED",
                "Assignment deadline has already passed",
                Map.of(
                        "assignmentId", assignmentId,
                        "deadline", deadline
                )
        );
    }
}

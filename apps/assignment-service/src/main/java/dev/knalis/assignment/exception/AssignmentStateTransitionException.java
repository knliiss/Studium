package dev.knalis.assignment.exception;

import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AssignmentStateTransitionException extends AppException {

    public AssignmentStateTransitionException(
            UUID assignmentId,
            AssignmentStatus fromStatus,
            AssignmentStatus targetStatus
    ) {
        super(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "Assignment state transition is not allowed",
                details(assignmentId, fromStatus, targetStatus)
        );
    }

    private static Map<String, Object> details(
            UUID assignmentId,
            AssignmentStatus fromStatus,
            AssignmentStatus targetStatus
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("assignmentId", assignmentId);
        details.put("fromStatus", fromStatus.name());
        details.put("targetStatus", targetStatus.name());
        return details;
    }
}

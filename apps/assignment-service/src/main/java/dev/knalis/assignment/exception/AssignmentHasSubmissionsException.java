package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AssignmentHasSubmissionsException extends AppException {

    public AssignmentHasSubmissionsException(UUID assignmentId, long submissionsCount) {
        super(
                HttpStatus.CONFLICT,
                "ASSIGNMENT_HAS_SUBMISSIONS",
                "Assignment has submissions and cannot be permanently deleted",
                details(assignmentId, submissionsCount)
        );
    }

    private static Map<String, Object> details(UUID assignmentId, long submissionsCount) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("assignmentId", assignmentId);
        details.put("submissionsCount", submissionsCount);
        return details;
    }
}

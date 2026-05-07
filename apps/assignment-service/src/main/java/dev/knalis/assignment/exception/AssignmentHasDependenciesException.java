package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AssignmentHasDependenciesException extends AppException {

    public AssignmentHasDependenciesException(UUID assignmentId, String dependencyType) {
        super(
                HttpStatus.CONFLICT,
                "ASSIGNMENT_HAS_DEPENDENCIES",
                "Assignment has dependencies and cannot be permanently deleted",
                details(assignmentId, dependencyType)
        );
    }

    private static Map<String, Object> details(UUID assignmentId, String dependencyType) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("assignmentId", assignmentId);
        details.put("dependencyType", dependencyType);
        return details;
    }
}

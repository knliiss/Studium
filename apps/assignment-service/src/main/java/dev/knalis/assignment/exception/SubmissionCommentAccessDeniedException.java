package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class SubmissionCommentAccessDeniedException extends AppException {

    public SubmissionCommentAccessDeniedException(UUID submissionId, UUID currentUserId) {
        super(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access to submission comments is denied",
                details(submissionId, currentUserId)
        );
    }

    public SubmissionCommentAccessDeniedException(UUID submissionId, UUID commentId, UUID currentUserId) {
        super(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Modification of the submission comment is denied",
                details(submissionId, commentId, currentUserId)
        );
    }

    private static Map<String, Object> details(UUID submissionId, UUID currentUserId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("submissionId", submissionId);
        details.put("currentUserId", currentUserId);
        return details;
    }

    private static Map<String, Object> details(UUID submissionId, UUID commentId, UUID currentUserId) {
        Map<String, Object> details = details(submissionId, currentUserId);
        details.put("commentId", commentId);
        return details;
    }
}

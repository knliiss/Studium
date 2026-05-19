package dev.knalis.assignment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubmissionDetailResponse(
        UUID assignmentId,
        UUID studentId,
        String currentStatus,
        int currentAttemptNumber,
        int maxAttempts,
        int remainingAttempts,
        Instant revisionDeadline,
        String latestTeacherComment,
        Integer finalScore,
        int maxScore,
        SubmissionAttemptDetailResponse currentAttempt,
        List<SubmissionAttemptDetailResponse> attempts
) {
}

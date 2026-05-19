package dev.knalis.assignment.factory.submission;

import dev.knalis.assignment.entity.SubmissionStatus;
import dev.knalis.assignment.entity.Submission;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SubmissionFactory {
    
    public Submission newSubmission(
            UUID assignmentId,
            UUID userId,
            UUID fileId,
            SubmissionStatus status,
            int attemptNumber,
            boolean submittedLate
    ) {
        Submission submission = new Submission();
        submission.setAssignmentId(assignmentId);
        submission.setUserId(userId);
        submission.setFileId(fileId);
        submission.setStatus(status);
        submission.setAttemptNumber(attemptNumber);
        submission.setSubmittedLate(submittedLate);
        return submission;
    }
}

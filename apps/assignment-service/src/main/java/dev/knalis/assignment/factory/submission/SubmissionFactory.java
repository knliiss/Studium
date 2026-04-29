package dev.knalis.assignment.factory.submission;

import dev.knalis.assignment.entity.Submission;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SubmissionFactory {
    
    public Submission newSubmission(UUID assignmentId, UUID userId, UUID fileId) {
        Submission submission = new Submission();
        submission.setAssignmentId(assignmentId);
        submission.setUserId(userId);
        submission.setFileId(fileId);
        return submission;
    }
}

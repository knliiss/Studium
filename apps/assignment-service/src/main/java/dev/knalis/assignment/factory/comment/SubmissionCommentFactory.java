package dev.knalis.assignment.factory.comment;

import dev.knalis.assignment.entity.SubmissionComment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SubmissionCommentFactory {

    public SubmissionComment newComment(UUID submissionId, UUID authorUserId, String body) {
        SubmissionComment comment = new SubmissionComment();
        comment.setSubmissionId(submissionId);
        comment.setAuthorUserId(authorUserId);
        comment.setBody(body.trim());
        comment.setDeleted(false);
        return comment;
    }
}

package dev.knalis.assignment.service.comment;

import dev.knalis.assignment.dto.request.UpsertSubmissionCommentRequest;
import dev.knalis.assignment.dto.response.SubmissionCommentResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.entity.SubmissionComment;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.exception.SubmissionCommentAccessDeniedException;
import dev.knalis.assignment.exception.SubmissionCommentNotFoundException;
import dev.knalis.assignment.exception.SubmissionNotFoundException;
import dev.knalis.assignment.factory.comment.SubmissionCommentFactory;
import dev.knalis.assignment.mapper.SubmissionCommentMapper;
import dev.knalis.assignment.repository.SubmissionCommentRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubmissionCommentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionCommentRepository submissionCommentRepository;
    private final SubmissionCommentFactory submissionCommentFactory;
    private final SubmissionCommentMapper submissionCommentMapper;

    @Transactional
    public SubmissionCommentResponse createComment(
            UUID currentUserId,
            boolean admin,
            boolean teacher,
            UUID submissionId,
            UpsertSubmissionCommentRequest request
    ) {
        Submission submission = requireSubmission(submissionId);
        assertSubmissionAccess(submission, currentUserId, admin, teacher);

        SubmissionComment comment = submissionCommentFactory.newComment(submissionId, currentUserId, request.body());
        return submissionCommentMapper.toResponse(submissionCommentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public List<SubmissionCommentResponse> getComments(
            UUID currentUserId,
            boolean admin,
            boolean teacher,
            UUID submissionId
    ) {
        Submission submission = requireSubmission(submissionId);
        assertSubmissionAccess(submission, currentUserId, admin, teacher);

        return submissionCommentRepository.findAllBySubmissionIdOrderByCreatedAtAsc(submissionId).stream()
                .map(submissionCommentMapper::toResponse)
                .toList();
    }

    @Transactional
    public SubmissionCommentResponse updateComment(
            UUID currentUserId,
            boolean admin,
            boolean teacher,
            UUID submissionId,
            UUID commentId,
            UpsertSubmissionCommentRequest request
    ) {
        Submission submission = requireSubmission(submissionId);
        assertSubmissionAccess(submission, currentUserId, admin, teacher);

        SubmissionComment comment = submissionCommentRepository.findByIdAndSubmissionId(commentId, submissionId)
                .orElseThrow(() -> new SubmissionCommentNotFoundException(commentId));
        assertCommentMutationAllowed(comment, submissionId, currentUserId, admin);

        comment.setBody(request.body().trim());
        comment.setDeleted(false);
        return submissionCommentMapper.toResponse(submissionCommentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(
            UUID currentUserId,
            boolean admin,
            boolean teacher,
            UUID submissionId,
            UUID commentId
    ) {
        Submission submission = requireSubmission(submissionId);
        assertSubmissionAccess(submission, currentUserId, admin, teacher);

        SubmissionComment comment = submissionCommentRepository.findByIdAndSubmissionId(commentId, submissionId)
                .orElseThrow(() -> new SubmissionCommentNotFoundException(commentId));
        assertCommentMutationAllowed(comment, submissionId, currentUserId, admin);

        if (comment.isDeleted()) {
            return;
        }

        comment.setDeleted(true);
        submissionCommentRepository.save(comment);
    }

    private Submission requireSubmission(UUID submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
    }

    private void assertSubmissionAccess(Submission submission, UUID currentUserId, boolean admin, boolean teacher) {
        if (admin) {
            return;
        }
        if (teacher) {
            Assignment assignment = assignmentRepository.findById(submission.getAssignmentId())
                    .orElse(null);
            if (assignment != null
                    && assignment.getCreatedByUserId() != null
                    && assignment.getCreatedByUserId().equals(currentUserId)) {
                return;
            }
        }
        if (submission.getUserId().equals(currentUserId)) {
            return;
        }
        throw new SubmissionCommentAccessDeniedException(submission.getId(), currentUserId);
    }

    private void assertCommentMutationAllowed(
            SubmissionComment comment,
            UUID submissionId,
            UUID currentUserId,
            boolean admin
    ) {
        if (admin || comment.getAuthorUserId().equals(currentUserId)) {
            return;
        }
        throw new SubmissionCommentAccessDeniedException(submissionId, comment.getId(), currentUserId);
    }
}

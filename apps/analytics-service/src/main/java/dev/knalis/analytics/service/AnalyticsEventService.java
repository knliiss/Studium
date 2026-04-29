package dev.knalis.analytics.service;

import dev.knalis.analytics.entity.RawAcademicEvent;
import dev.knalis.analytics.repository.RawAcademicEventRepository;
import dev.knalis.contracts.event.AssignmentCreatedEventV1;
import dev.knalis.contracts.event.AssignmentOpenedEventV1;
import dev.knalis.contracts.event.AssignmentSubmittedEventV1;
import dev.knalis.contracts.event.DeadlineMissedEntityTypeV1;
import dev.knalis.contracts.event.DeadlineMissedEventV1;
import dev.knalis.contracts.event.GradeAssignedEventV1;
import dev.knalis.contracts.event.LectureOpenedEventV1;
import dev.knalis.contracts.event.TestCompletedEventV1;
import dev.knalis.contracts.event.TestPublishedEventV1;
import dev.knalis.contracts.event.TestStartedEventV1;
import dev.knalis.contracts.event.TopicOpenedEventV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsEventService {
    
    private static final String ASSIGNMENT_SUBMITTED_EVENT = "AssignmentSubmittedEventV1";
    private static final String TEST_PUBLISHED_EVENT = "TestPublishedEventV1";
    
    private final AnalyticsContextResolver analyticsContextResolver;
    private final RawAcademicEventService rawAcademicEventService;
    private final RawAcademicEventRepository rawAcademicEventRepository;
    private final StudentProgressSnapshotService studentProgressSnapshotService;
    private final SubjectAnalyticsSnapshotService subjectAnalyticsSnapshotService;
    private final TeacherAnalyticsSnapshotService teacherAnalyticsSnapshotService;
    
    @Transactional
    public void handle(LectureOpenedEventV1 event) {
        UUID groupId = analyticsContextResolver.resolveGroupId(null, event.subjectId(), event.topicId());
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                event.userId(),
                null,
                groupId,
                event.subjectId(),
                event.topicId(),
                null,
                null,
                null,
                event.occurredAt(),
                event
        )) {
            return;
        }
        studentProgressSnapshotService.recordLectureOpened(event.userId(), groupId, event.occurredAt());
        subjectAnalyticsSnapshotService.recordLectureOpened(event.subjectId(), groupId);
    }
    
    @Transactional
    public void handle(TopicOpenedEventV1 event) {
        UUID groupId = analyticsContextResolver.resolveGroupId(null, event.subjectId(), event.topicId());
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                event.userId(),
                null,
                groupId,
                event.subjectId(),
                event.topicId(),
                null,
                null,
                null,
                event.occurredAt(),
                event
        )) {
            return;
        }
        studentProgressSnapshotService.recordTopicOpened(event.userId(), groupId, event.occurredAt());
    }
    
    @Transactional
    public void handle(AssignmentOpenedEventV1 event) {
        UUID groupId = analyticsContextResolver.resolveGroupId(null, event.subjectId(), event.topicId());
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                event.userId(),
                null,
                groupId,
                event.subjectId(),
                event.topicId(),
                event.assignmentId(),
                null,
                null,
                event.occurredAt(),
                event
        )) {
            return;
        }
        studentProgressSnapshotService.recordAssignmentOpened(event.userId(), groupId, event.occurredAt());
        subjectAnalyticsSnapshotService.recordAssignmentOpened(event.subjectId(), groupId);
    }
    
    @Transactional
    public void handle(AssignmentSubmittedEventV1 event) {
        UUID groupId = analyticsContextResolver.resolveGroupId(null, event.subjectId(), event.topicId());
        Instant activityTime = event.submittedAt() != null ? event.submittedAt() : event.occurredAt();
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                event.userId(),
                null,
                groupId,
                event.subjectId(),
                event.topicId(),
                event.assignmentId(),
                event.submissionId(),
                null,
                event.occurredAt(),
                event
        )) {
            return;
        }
        studentProgressSnapshotService.recordAssignmentSubmitted(event.userId(), groupId, event.wasLate(), activityTime);
        subjectAnalyticsSnapshotService.recordAssignmentSubmitted(event.subjectId(), groupId, event.wasLate());
    }
    
    @Transactional
    public void handle(DeadlineMissedEventV1 event) {
        UUID subjectId = analyticsContextResolver.resolveSubjectId(event.subjectId(), event.topicId());
        UUID groupId = analyticsContextResolver.resolveGroupId(null, subjectId, event.topicId());
        UUID assignmentId = event.entityType() == DeadlineMissedEntityTypeV1.ASSIGNMENT ? event.entityId() : null;
        UUID testId = event.entityType() == DeadlineMissedEntityTypeV1.TEST ? event.entityId() : null;
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                event.userId(),
                null,
                groupId,
                subjectId,
                event.topicId(),
                assignmentId,
                null,
                testId,
                event.occurredAt(),
                event
        )) {
            return;
        }
        studentProgressSnapshotService.recordDeadlineMissed(event.userId(), groupId, event.occurredAt());
        subjectAnalyticsSnapshotService.recordDeadlineMissed(subjectId, groupId);
    }
    
    @Transactional
    public void handle(TestStartedEventV1 event) {
        UUID groupId = analyticsContextResolver.resolveGroupId(null, event.subjectId(), event.topicId());
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                event.userId(),
                null,
                groupId,
                event.subjectId(),
                event.topicId(),
                null,
                null,
                event.testId(),
                event.occurredAt(),
                event
        )) {
            return;
        }
        studentProgressSnapshotService.recordTestStarted(event.userId(), groupId, event.occurredAt());
        subjectAnalyticsSnapshotService.recordTestStarted(event.subjectId(), groupId);
    }
    
    @Transactional
    public void handle(TestCompletedEventV1 event) {
        UUID groupId = analyticsContextResolver.resolveGroupId(null, event.subjectId(), event.topicId());
        double normalizedScore = normalizeScore(event.score(), event.maxScore());
        Instant activityTime = event.completedAt() != null ? event.completedAt() : event.occurredAt();
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                event.userId(),
                null,
                groupId,
                event.subjectId(),
                event.topicId(),
                null,
                null,
                event.testId(),
                event.occurredAt(),
                event
        )) {
            return;
        }
        studentProgressSnapshotService.recordTestCompleted(event.userId(), groupId, normalizedScore, activityTime);
        subjectAnalyticsSnapshotService.recordTestCompleted(event.subjectId(), groupId, normalizedScore);
        teacherAnalyticsSnapshotService.recordTestCompleted(resolvePublishedTeacherId(event.testId()), normalizedScore);
    }
    
    @Transactional
    public void handle(AssignmentCreatedEventV1 event) {
        UUID subjectId = analyticsContextResolver.resolveSubjectId(null, event.topicId());
        UUID groupId = analyticsContextResolver.resolveGroupId(null, subjectId, event.topicId());
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                null,
                event.createdByUserId(),
                groupId,
                subjectId,
                event.topicId(),
                event.assignmentId(),
                null,
                null,
                event.occurredAt(),
                event
        )) {
            return;
        }
        for (UUID userId : analyticsContextResolver.resolveGroupStudentUserIds(groupId)) {
            studentProgressSnapshotService.recordAssignmentCreated(userId, groupId);
        }
        subjectAnalyticsSnapshotService.recordAssignmentCreated(subjectId, groupId);
        teacherAnalyticsSnapshotService.recordAssignmentCreated(event.createdByUserId());
    }
    
    @Transactional
    public void handle(GradeAssignedEventV1 event) {
        UUID subjectId = analyticsContextResolver.resolveSubjectId(event.subjectId(), event.topicId());
        UUID groupId = analyticsContextResolver.resolveGroupId(null, subjectId, event.topicId());
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                event.studentUserId(),
                event.assignedByUserId(),
                groupId,
                subjectId,
                event.topicId(),
                event.assignmentId(),
                event.submissionId(),
                null,
                event.occurredAt(),
                event
        )) {
            return;
        }
        studentProgressSnapshotService.recordGradeAssigned(event.studentUserId(), groupId, event.score(), event.occurredAt());
        subjectAnalyticsSnapshotService.recordGradeAssigned(subjectId, groupId, event.score());
        teacherAnalyticsSnapshotService.recordGradeAssigned(
                event.assignedByUserId(),
                event.score(),
                resolveReviewTimeHours(event.submissionId(), event.occurredAt())
        );
    }
    
    @Transactional
    public void handle(TestPublishedEventV1 event) {
        UUID subjectId = analyticsContextResolver.resolveSubjectId(null, event.topicId());
        UUID groupId = analyticsContextResolver.resolveGroupId(null, subjectId, event.topicId());
        if (!rawAcademicEventService.storeIfAbsent(
                event.eventId(),
                event.getClass().getSimpleName(),
                null,
                event.publishedByUserId(),
                groupId,
                subjectId,
                event.topicId(),
                null,
                null,
                event.testId(),
                event.occurredAt(),
                event
        )) {
            return;
        }
        subjectAnalyticsSnapshotService.recordTestPublished(subjectId, groupId);
        teacherAnalyticsSnapshotService.recordTestPublished(event.publishedByUserId());
    }
    
    private UUID resolvePublishedTeacherId(UUID testId) {
        return rawAcademicEventRepository.findTopByTestIdAndEventTypeOrderByOccurredAtDesc(testId, TEST_PUBLISHED_EVENT)
                .map(RawAcademicEvent::getTeacherId)
                .orElse(null);
    }
    
    private Double resolveReviewTimeHours(UUID submissionId, Instant gradedAt) {
        return rawAcademicEventRepository.findTopBySubmissionIdAndEventTypeOrderByOccurredAtDesc(
                        submissionId,
                        ASSIGNMENT_SUBMITTED_EVENT
                )
                .map(RawAcademicEvent::getOccurredAt)
                .filter(submittedAt -> gradedAt != null && !gradedAt.isBefore(submittedAt))
                .map(submittedAt -> Duration.between(submittedAt, gradedAt).toMinutes() / 60.0)
                .orElse(null);
    }
    
    private double normalizeScore(Number score, Number maxScore) {
        if (score == null || maxScore == null || maxScore.doubleValue() <= 0) {
            return 0;
        }
        return Math.round(((score.doubleValue() / maxScore.doubleValue()) * 100.0) * 100.0) / 100.0;
    }
}

package dev.knalis.analytics.service;

import dev.knalis.analytics.repository.RawAcademicEventRepository;
import dev.knalis.contracts.event.AssignmentCreatedEventV1;
import dev.knalis.contracts.event.GradeAssignedEventV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventServiceTest {
    
    @Mock
    private AnalyticsContextResolver analyticsContextResolver;
    
    @Mock
    private RawAcademicEventService rawAcademicEventService;
    
    @Mock
    private RawAcademicEventRepository rawAcademicEventRepository;
    
    @Mock
    private StudentProgressSnapshotService studentProgressSnapshotService;
    
    @Mock
    private SubjectAnalyticsSnapshotService subjectAnalyticsSnapshotService;
    
    @Mock
    private TeacherAnalyticsSnapshotService teacherAnalyticsSnapshotService;
    
    private AnalyticsEventService analyticsEventService;
    
    @BeforeEach
    void setUp() {
        analyticsEventService = new AnalyticsEventService(
                analyticsContextResolver,
                rawAcademicEventService,
                rawAcademicEventRepository,
                studentProgressSnapshotService,
                subjectAnalyticsSnapshotService,
                teacherAnalyticsSnapshotService
        );
    }
    
    @Test
    void handleAssignmentCreatedSkipsDuplicateEvent() {
        AssignmentCreatedEventV1 event = new AssignmentCreatedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Assignment",
                Instant.now().plusSeconds(3600),
                UUID.randomUUID()
        );
        
        when(analyticsContextResolver.resolveSubjectId(null, event.topicId())).thenReturn(UUID.randomUUID());
        when(analyticsContextResolver.resolveGroupId(any(), any(), any())).thenReturn(UUID.randomUUID());
        when(rawAcademicEventService.storeIfAbsent(
                eq(event.eventId()),
                eq("AssignmentCreatedEventV1"),
                isNull(),
                eq(event.createdByUserId()),
                any(),
                any(),
                eq(event.topicId()),
                eq(event.assignmentId()),
                isNull(),
                isNull(),
                eq(event.occurredAt()),
                eq(event)
        )).thenReturn(false);
        
        analyticsEventService.handle(event);
        
        verifyNoInteractions(studentProgressSnapshotService, subjectAnalyticsSnapshotService, teacherAnalyticsSnapshotService);
    }
    
    @Test
    void handleGradeAssignedUpdatesRelevantSnapshots() {
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        GradeAssignedEventV1 event = new GradeAssignedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                92,
                "Great",
                UUID.randomUUID(),
                subjectId,
                UUID.randomUUID()
        );
        
        when(analyticsContextResolver.resolveSubjectId(subjectId, event.topicId())).thenReturn(subjectId);
        when(analyticsContextResolver.resolveGroupId(null, subjectId, event.topicId())).thenReturn(groupId);
        when(rawAcademicEventService.storeIfAbsent(
                eq(event.eventId()),
                eq("GradeAssignedEventV1"),
                eq(event.studentUserId()),
                eq(event.assignedByUserId()),
                eq(groupId),
                eq(subjectId),
                eq(event.topicId()),
                eq(event.assignmentId()),
                eq(event.submissionId()),
                isNull(),
                eq(event.occurredAt()),
                eq(event)
        )).thenReturn(true);
        when(rawAcademicEventRepository.findTopBySubmissionIdAndEventTypeOrderByOccurredAtDesc(
                event.submissionId(),
                "AssignmentSubmittedEventV1"
        )).thenReturn(Optional.empty());
        
        analyticsEventService.handle(event);
        
        verify(studentProgressSnapshotService).recordGradeAssigned(event.studentUserId(), groupId, 92, event.occurredAt());
        verify(subjectAnalyticsSnapshotService).recordGradeAssigned(subjectId, groupId, 92);
        verify(teacherAnalyticsSnapshotService).recordGradeAssigned(event.assignedByUserId(), 92, null);
    }
}

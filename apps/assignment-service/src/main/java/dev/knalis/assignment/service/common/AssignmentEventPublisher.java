package dev.knalis.assignment.service.common;

import dev.knalis.assignment.config.KafkaTopicsProperties;
import dev.knalis.contracts.event.AssignmentCreatedEventV1;
import dev.knalis.contracts.event.AssignmentOpenedEventV1;
import dev.knalis.contracts.event.AssignmentSubmittedEventV1;
import dev.knalis.contracts.event.AssignmentUpdatedEventV1;
import dev.knalis.contracts.event.DeadlineMissedEventV1;
import dev.knalis.contracts.event.GradeAssignedEventV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentEventPublisher {
    
    private final AssignmentOutboxService assignmentOutboxService;
    private final KafkaTopicsProperties topics;
    
    public void publishAssignmentCreated(AssignmentCreatedEventV1 event) {
        assignmentOutboxService.enqueue(topics.getAssignmentCreated(), event.assignmentId().toString(), event);
    }
    
    public void publishAssignmentUpdated(AssignmentUpdatedEventV1 event) {
        assignmentOutboxService.enqueue(topics.getAssignmentUpdated(), event.assignmentId().toString(), event);
    }
    
    public void publishGradeAssigned(GradeAssignedEventV1 event) {
        assignmentOutboxService.enqueue(topics.getGradeAssigned(), event.gradeId().toString(), event);
    }
    
    public void publishAssignmentOpened(AssignmentOpenedEventV1 event) {
        assignmentOutboxService.enqueue(topics.getAssignmentOpened(), event.assignmentId().toString(), event);
    }
    
    public void publishAssignmentSubmitted(AssignmentSubmittedEventV1 event) {
        assignmentOutboxService.enqueue(topics.getAssignmentSubmitted(), event.submissionId().toString(), event);
    }
    
    public void publishDeadlineMissed(DeadlineMissedEventV1 event) {
        assignmentOutboxService.enqueue(topics.getDeadlineMissed(), event.entityId().toString(), event);
    }
}

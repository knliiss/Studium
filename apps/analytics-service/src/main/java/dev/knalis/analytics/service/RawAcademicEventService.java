package dev.knalis.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.analytics.entity.RawAcademicEvent;
import dev.knalis.analytics.repository.RawAcademicEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RawAcademicEventService {
    
    private final RawAcademicEventRepository rawAcademicEventRepository;
    private final ObjectMapper objectMapper;
    
    public boolean storeIfAbsent(
            UUID eventId,
            String eventType,
            UUID userId,
            UUID teacherId,
            UUID groupId,
            UUID subjectId,
            UUID topicId,
            UUID assignmentId,
            UUID submissionId,
            UUID testId,
            Instant occurredAt,
            Object payload
    ) {
        RawAcademicEvent rawAcademicEvent = new RawAcademicEvent();
        rawAcademicEvent.setEventId(eventId);
        rawAcademicEvent.setEventType(eventType);
        rawAcademicEvent.setUserId(userId);
        rawAcademicEvent.setTeacherId(teacherId);
        rawAcademicEvent.setGroupId(groupId);
        rawAcademicEvent.setSubjectId(subjectId);
        rawAcademicEvent.setTopicId(topicId);
        rawAcademicEvent.setAssignmentId(assignmentId);
        rawAcademicEvent.setSubmissionId(submissionId);
        rawAcademicEvent.setTestId(testId);
        rawAcademicEvent.setOccurredAt(occurredAt);
        rawAcademicEvent.setPayloadJson(serializePayload(payload));
        
        try {
            rawAcademicEventRepository.save(rawAcademicEvent);
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (rawAcademicEventRepository.existsByEventId(eventId)) {
                return false;
            }
            throw exception;
        }
    }
    
    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize academic event payload", exception);
        }
    }
}

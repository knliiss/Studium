package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.dto.request.ScheduleConflictCheckRequest;
import dev.knalis.schedule.dto.response.ScheduleConflictCheckResponse;
import dev.knalis.schedule.dto.response.ScheduleConflictItemResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.ScheduleOverride;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.exception.AcademicSemesterNotFoundException;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.exception.ScheduleValidationException;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleConflictPreviewService {
    
    private final AcademicSemesterRepository academicSemesterRepository;
    private final ScheduleConflictService scheduleConflictService;
    
    @Transactional(readOnly = true)
    public ScheduleConflictCheckResponse check(ScheduleConflictCheckRequest request) {
        if (request.date() != null || request.overrideType() != null) {
            return previewOverride(request);
        }
        return previewTemplate(request);
    }
    
    private ScheduleConflictCheckResponse previewTemplate(ScheduleConflictCheckRequest request) {
        requireTemplateRequest(request);
        
        ScheduleTemplate candidate = new ScheduleTemplate();
        candidate.setSemesterId(requireSemester(request.semesterId()).getId());
        candidate.setGroupId(request.groupId());
        candidate.setSubjectId(request.subjectId());
        candidate.setTeacherId(request.teacherId());
        candidate.setDayOfWeek(request.dayOfWeek());
        candidate.setSlotId(request.slotId());
        candidate.setWeekType(request.weekType());
        candidate.setLessonType(request.lessonType());
        candidate.setLessonFormat(request.lessonFormat());
        candidate.setRoomId(request.lessonFormat() == LessonFormat.ONLINE ? null : request.roomId());
        candidate.setActive(true);
        
        try {
            scheduleConflictService.assertNoTemplateConflicts(candidate, request.templateId(), List.of());
            return new ScheduleConflictCheckResponse(false, List.of());
        } catch (ScheduleConflictException exception) {
            return new ScheduleConflictCheckResponse(
                    true,
                    List.of(toConflictItem(exception, request.slotId(), null, request.dayOfWeek()))
            );
        }
    }
    
    private ScheduleConflictCheckResponse previewOverride(ScheduleConflictCheckRequest request) {
        requireOverrideRequest(request);
        
        if (request.overrideType() == OverrideType.CANCEL) {
            return new ScheduleConflictCheckResponse(false, List.of());
        }
        
        AcademicSemester semester = requireSemester(request.semesterId());
        ScheduleOverride candidate = new ScheduleOverride();
        candidate.setSemesterId(semester.getId());
        candidate.setTemplateId(request.templateId());
        candidate.setOverrideType(request.overrideType() == null ? OverrideType.EXTRA : request.overrideType());
        candidate.setDate(request.date());
        candidate.setGroupId(request.groupId());
        candidate.setSubjectId(request.subjectId());
        candidate.setTeacherId(request.teacherId());
        candidate.setSlotId(request.slotId());
        candidate.setLessonType(request.lessonType());
        candidate.setLessonFormat(request.lessonFormat());
        candidate.setRoomId(request.lessonFormat() == LessonFormat.ONLINE ? null : request.roomId());
        
        try {
            scheduleConflictService.assertNoOverrideConflicts(
                    candidate,
                    semester,
                    request.overrideId(),
                    request.templateId()
            );
            return new ScheduleConflictCheckResponse(false, List.of());
        } catch (ScheduleConflictException exception) {
            return new ScheduleConflictCheckResponse(
                    true,
                    List.of(toConflictItem(exception, request.slotId(), request.date(), null))
            );
        }
    }
    
    private AcademicSemester requireSemester(UUID semesterId) {
        if (semesterId == null) {
            throw new ScheduleValidationException("SEMESTER_ID_REQUIRED", "semesterId is required");
        }
        
        return academicSemesterRepository.findById(semesterId)
                .orElseThrow(() -> new AcademicSemesterNotFoundException(semesterId));
    }
    
    private void requireTemplateRequest(ScheduleConflictCheckRequest request) {
        if (request.semesterId() == null || request.dayOfWeek() == null || request.slotId() == null
                || request.weekType() == null || request.groupId() == null || request.subjectId() == null
                || request.teacherId() == null || request.lessonFormat() == null || request.lessonType() == null) {
            throw new ScheduleValidationException(
                    "INVALID_CONFLICT_CHECK_REQUEST",
                    "Template conflict checks require semesterId, dayOfWeek, slotId, weekType, groupId, subjectId, teacherId, lessonType, and lessonFormat"
            );
        }
    }
    
    private void requireOverrideRequest(ScheduleConflictCheckRequest request) {
        if (request.semesterId() == null || request.date() == null || request.overrideType() == null) {
            throw new ScheduleValidationException(
                    "INVALID_CONFLICT_CHECK_REQUEST",
                    "Override conflict checks require semesterId, date, and overrideType"
            );
        }
        if (request.overrideType() != OverrideType.CANCEL
                && (request.slotId() == null || request.groupId() == null || request.subjectId() == null
                || request.teacherId() == null || request.lessonFormat() == null || request.lessonType() == null)) {
            throw new ScheduleValidationException(
                    "INVALID_CONFLICT_CHECK_REQUEST",
                    "Override conflict checks require groupId, subjectId, teacherId, slotId, lessonType, and lessonFormat"
            );
        }
    }
    
    private ScheduleConflictItemResponse toConflictItem(
            ScheduleConflictException exception,
            UUID slotId,
            LocalDate date,
            DayOfWeek dayOfWeek
    ) {
        Map<String, Object> details = exception.getDetails();
        UUID conflictingEntityId = null;
        String conflictingEntityType = null;
        if (details.containsKey("existingTemplateId")) {
            conflictingEntityId = (UUID) details.get("existingTemplateId");
            conflictingEntityType = "SCHEDULE_TEMPLATE";
        } else if (details.containsKey("existingOverrideId")) {
            conflictingEntityId = (UUID) details.get("existingOverrideId");
            conflictingEntityType = "SCHEDULE_OVERRIDE";
        }
        
        return new ScheduleConflictItemResponse(
                toFrontendConflictType((String) details.get("conflictType")),
                exception.getMessage(),
                conflictingEntityId,
                conflictingEntityType,
                date,
                dayOfWeek,
                slotId
        );
    }
    
    private String toFrontendConflictType(String conflictType) {
        return switch (conflictType) {
            case "TEACHER" -> "TEACHER_CONFLICT";
            case "ROOM" -> "ROOM_CONFLICT";
            default -> "GROUP_CONFLICT";
        };
    }
}

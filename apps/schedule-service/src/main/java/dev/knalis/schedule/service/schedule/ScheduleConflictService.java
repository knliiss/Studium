package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.ScheduleOverride;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.WeekType;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.exception.ScheduleValidationException;
import dev.knalis.schedule.repository.ScheduleOverrideRepository;
import dev.knalis.schedule.repository.ScheduleTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleConflictService {
    
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final ScheduleOverrideRepository scheduleOverrideRepository;
    
    public void assertNoTemplateConflicts(
            ScheduleTemplate candidate,
            UUID excludedTemplateId,
            List<ScheduleTemplate> stagedTemplates
    ) {
        if (!candidate.isActive()) {
            return;
        }
        
        List<ScheduleTemplate> templatesToCheck = new ArrayList<>();
        templatesToCheck.addAll(scheduleTemplateRepository.findAllBySemesterIdAndDayOfWeekAndSlotIdAndActiveTrue(
                candidate.getSemesterId(),
                candidate.getDayOfWeek(),
                candidate.getSlotId()
        ));
        templatesToCheck.addAll(
                stagedTemplates.stream()
                        .filter(ScheduleTemplate::isActive)
                        .filter(template -> candidate.getSemesterId().equals(template.getSemesterId()))
                        .filter(template -> candidate.getDayOfWeek() == template.getDayOfWeek())
                        .filter(template -> candidate.getSlotId().equals(template.getSlotId()))
                        .toList()
        );
        
        for (ScheduleTemplate existing : templatesToCheck) {
            if (excludedTemplateId != null && excludedTemplateId.equals(existing.getId())) {
                continue;
            }
            if (!weekTypesOverlap(candidate.getWeekType(), existing.getWeekType())) {
                continue;
            }
            if (candidate.getGroupId().equals(existing.getGroupId())) {
                throw new ScheduleConflictException(
                        "GROUP_SCHEDULE_CONFLICT",
                        "Group already has a lesson in the selected semester, day, slot, and week pattern",
                        buildTemplateConflictDetails(candidate, existing, "GROUP")
                );
            }
            if (candidate.getTeacherId().equals(existing.getTeacherId())) {
                throw new ScheduleConflictException(
                        "TEACHER_SCHEDULE_CONFLICT",
                        "Teacher already has a lesson in the selected semester, day, slot, and week pattern",
                        buildTemplateConflictDetails(candidate, existing, "TEACHER")
                );
            }
            if (candidate.getLessonFormat() == LessonFormat.OFFLINE
                    && existing.getLessonFormat() == LessonFormat.OFFLINE
                    && candidate.getRoomId() != null
                    && candidate.getRoomId().equals(existing.getRoomId())) {
                throw new ScheduleConflictException(
                        "ROOM_SCHEDULE_CONFLICT",
                        "Room is already used in the selected semester, day, slot, and week pattern",
                        buildTemplateConflictDetails(candidate, existing, "ROOM")
                );
            }
        }
    }
    
    public void assertTemplateOccurrenceExists(ScheduleTemplate template, AcademicSemester semester, LocalDate date) {
        if (!AcademicWeekSupport.isTemplateOccurrence(semester, template, date)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("templateId", template.getId());
            details.put("semesterId", semester.getId());
            details.put("date", date.toString());
            
            throw new ScheduleValidationException(
                    "TEMPLATE_OCCURRENCE_NOT_FOUND",
                    "Template does not have an occurrence on the selected date",
                    details
            );
        }
    }
    
    public void assertNoDuplicateTemplateOverride(UUID templateId, LocalDate date, UUID excludedOverrideId) {
        boolean duplicateExists = scheduleOverrideRepository.findAllByTemplateIdAndDate(templateId, date).stream()
                .anyMatch(scheduleOverride -> excludedOverrideId == null || !excludedOverrideId.equals(scheduleOverride.getId()));
        
        if (duplicateExists) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("templateId", templateId);
            details.put("date", date.toString());
            
            throw new ScheduleConflictException(
                    "TEMPLATE_OCCURRENCE_OVERRIDE_ALREADY_EXISTS",
                    "Another override already exists for the selected template occurrence",
                    details
            );
        }
    }
    
    public void assertNoOverrideConflicts(
            ScheduleOverride candidate,
            AcademicSemester semester,
            UUID excludedOverrideId,
            UUID ignoredTemplateId
    ) {
        if (candidate.getOverrideType() == OverrideType.CANCEL) {
            return;
        }
        
        for (ScheduleOverride existing : scheduleOverrideRepository.findAllBySemesterIdAndDateAndSlotId(
                candidate.getSemesterId(),
                candidate.getDate(),
                candidate.getSlotId()
        )) {
            if (excludedOverrideId != null && excludedOverrideId.equals(existing.getId())) {
                continue;
            }
            if (existing.getOverrideType() == OverrideType.CANCEL) {
                continue;
            }
            assertNoLessonClash(candidate, existing);
        }
        
        for (ScheduleTemplate template : scheduleTemplateRepository.findAllBySemesterIdAndDayOfWeekAndSlotIdAndActiveTrue(
                candidate.getSemesterId(),
                candidate.getDate().getDayOfWeek(),
                candidate.getSlotId()
        )) {
            if (ignoredTemplateId != null && ignoredTemplateId.equals(template.getId())) {
                continue;
            }
            if (!AcademicWeekSupport.isTemplateOccurrence(semester, template, candidate.getDate())) {
                continue;
            }
            if (hasAnotherOverrideForOccurrence(template.getId(), candidate.getDate(), excludedOverrideId)) {
                continue;
            }
            assertNoLessonClash(candidate, template);
        }
    }
    
    private boolean hasAnotherOverrideForOccurrence(UUID templateId, LocalDate date, UUID excludedOverrideId) {
        return scheduleOverrideRepository.findAllByTemplateIdAndDate(templateId, date).stream()
                .anyMatch(scheduleOverride -> excludedOverrideId == null || !excludedOverrideId.equals(scheduleOverride.getId()));
    }
    
    private void assertNoLessonClash(ScheduleOverride candidate, ScheduleOverride existing) {
        if (candidate.getGroupId().equals(existing.getGroupId())) {
            throw new ScheduleConflictException(
                    "GROUP_SCHEDULE_CONFLICT",
                    "Group already has a lesson on the selected date and slot",
                    buildOverrideConflictDetails(candidate, existing, "GROUP")
            );
        }
        if (candidate.getTeacherId().equals(existing.getTeacherId())) {
            throw new ScheduleConflictException(
                    "TEACHER_SCHEDULE_CONFLICT",
                    "Teacher already has a lesson on the selected date and slot",
                    buildOverrideConflictDetails(candidate, existing, "TEACHER")
            );
        }
        if (candidate.getLessonFormat() == LessonFormat.OFFLINE
                && existing.getLessonFormat() == LessonFormat.OFFLINE
                && candidate.getRoomId() != null
                && candidate.getRoomId().equals(existing.getRoomId())) {
            throw new ScheduleConflictException(
                    "ROOM_SCHEDULE_CONFLICT",
                    "Room is already used on the selected date and slot",
                    buildOverrideConflictDetails(candidate, existing, "ROOM")
            );
        }
    }
    
    private void assertNoLessonClash(ScheduleOverride candidate, ScheduleTemplate existing) {
        if (candidate.getGroupId().equals(existing.getGroupId())) {
            throw new ScheduleConflictException(
                    "GROUP_SCHEDULE_CONFLICT",
                    "Group already has a lesson on the selected date and slot",
                    buildOverrideTemplateConflictDetails(candidate, existing, "GROUP")
            );
        }
        if (candidate.getTeacherId().equals(existing.getTeacherId())) {
            throw new ScheduleConflictException(
                    "TEACHER_SCHEDULE_CONFLICT",
                    "Teacher already has a lesson on the selected date and slot",
                    buildOverrideTemplateConflictDetails(candidate, existing, "TEACHER")
            );
        }
        if (candidate.getLessonFormat() == LessonFormat.OFFLINE
                && existing.getLessonFormat() == LessonFormat.OFFLINE
                && candidate.getRoomId() != null
                && candidate.getRoomId().equals(existing.getRoomId())) {
            throw new ScheduleConflictException(
                    "ROOM_SCHEDULE_CONFLICT",
                    "Room is already used on the selected date and slot",
                    buildOverrideTemplateConflictDetails(candidate, existing, "ROOM")
            );
        }
    }
    
    private boolean weekTypesOverlap(WeekType left, WeekType right) {
        return left == WeekType.ALL || right == WeekType.ALL || left == right;
    }
    
    private Map<String, Object> buildTemplateConflictDetails(
            ScheduleTemplate candidate,
            ScheduleTemplate existing,
            String conflictType
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("conflictType", conflictType);
        details.put("semesterId", candidate.getSemesterId());
        details.put("dayOfWeek", candidate.getDayOfWeek().name());
        details.put("slotId", candidate.getSlotId());
        details.put("weekType", candidate.getWeekType().name());
        if (existing.getId() != null) {
            details.put("existingTemplateId", existing.getId());
        }
        return details;
    }
    
    private Map<String, Object> buildOverrideConflictDetails(
            ScheduleOverride candidate,
            ScheduleOverride existing,
            String conflictType
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("conflictType", conflictType);
        details.put("semesterId", candidate.getSemesterId());
        details.put("date", candidate.getDate().toString());
        details.put("slotId", candidate.getSlotId());
        if (existing.getId() != null) {
            details.put("existingOverrideId", existing.getId());
        }
        return details;
    }
    
    private Map<String, Object> buildOverrideTemplateConflictDetails(
            ScheduleOverride candidate,
            ScheduleTemplate existing,
            String conflictType
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("conflictType", conflictType);
        details.put("semesterId", candidate.getSemesterId());
        details.put("date", candidate.getDate().toString());
        details.put("slotId", candidate.getSlotId());
        if (existing.getId() != null) {
            details.put("existingTemplateId", existing.getId());
        }
        return details;
    }
}

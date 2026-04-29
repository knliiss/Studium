package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.dto.response.ResolvedLessonResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonSlot;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.ResolvedLessonSourceType;
import dev.knalis.schedule.entity.ScheduleOverride;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.exception.ScheduleValidationException;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import dev.knalis.schedule.repository.ScheduleOverrideRepository;
import dev.knalis.schedule.repository.ScheduleTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleReadService {
    
    private final AcademicSemesterRepository academicSemesterRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final ScheduleOverrideRepository scheduleOverrideRepository;
    private final LessonSlotRepository lessonSlotRepository;
    
    @Transactional(readOnly = true)
    public List<ResolvedLessonResponse> getGroupWeek(UUID groupId, LocalDate startDate) {
        return resolve(groupId, null, null, null, startDate, startDate.plusDays(6), false);
    }
    
    @Transactional(readOnly = true)
    public List<ResolvedLessonResponse> getGroupRange(UUID groupId, LocalDate dateFrom, LocalDate dateTo) {
        return resolve(groupId, null, null, null, dateFrom, dateTo, false);
    }
    
    @Transactional(readOnly = true)
    public List<ResolvedLessonResponse> getTeacherWeek(UUID teacherId, LocalDate startDate) {
        return resolve(null, teacherId, null, null, startDate, startDate.plusDays(6), false);
    }
    
    @Transactional(readOnly = true)
    public List<ResolvedLessonResponse> getTeacherRange(UUID teacherId, LocalDate dateFrom, LocalDate dateTo) {
        return resolve(null, teacherId, null, null, dateFrom, dateTo, false);
    }
    
    @Transactional(readOnly = true)
    public List<ResolvedLessonResponse> getRoomWeek(UUID roomId, LocalDate startDate) {
        return resolve(null, null, roomId, null, startDate, startDate.plusDays(6), false);
    }
    
    @Transactional(readOnly = true)
    public List<ResolvedLessonResponse> getRoomRange(UUID roomId, LocalDate dateFrom, LocalDate dateTo) {
        return resolve(null, null, roomId, null, dateFrom, dateTo, false);
    }
    
    @Transactional(readOnly = true)
    public List<ResolvedLessonResponse> search(
            UUID groupId,
            UUID teacherId,
            UUID roomId,
            LessonType lessonType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return resolve(groupId, teacherId, roomId, lessonType, dateFrom, dateTo, true);
    }
    
    private List<ResolvedLessonResponse> resolve(
            UUID groupId,
            UUID teacherId,
            UUID roomId,
            LessonType lessonType,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean search
    ) {
        validateRange(dateFrom, dateTo);
        if (search && groupId == null && teacherId == null && roomId == null) {
            throw new ScheduleValidationException(
                    "SCHEDULE_SEARCH_FILTER_REQUIRED",
                    "At least one of groupId, teacherId, or roomId must be provided"
            );
        }
        
        List<AcademicSemester> semesters = academicSemesterRepository.findAllOverlapping(dateFrom, dateTo);
        if (semesters.isEmpty()) {
            return List.of();
        }
        
        Map<UUID, AcademicSemester> semestersById = semesters.stream()
                .collect(Collectors.toMap(AcademicSemester::getId, semester -> semester));
        List<UUID> semesterIds = semesters.stream().map(AcademicSemester::getId).toList();
        List<ScheduleTemplate> templates = scheduleTemplateRepository.findAllBySemesterIdInAndActiveTrue(semesterIds);
        List<ScheduleOverride> overrides = scheduleOverrideRepository.findAllBySemesterIdInAndDateBetween(
                semesterIds,
                dateFrom,
                dateTo
        );
        
        Map<OccurrenceKey, ScheduleOverride> templateOverrides = overrides.stream()
                .filter(scheduleOverride -> scheduleOverride.getTemplateId() != null)
                .collect(Collectors.toMap(
                        scheduleOverride -> new OccurrenceKey(scheduleOverride.getTemplateId(), scheduleOverride.getDate()),
                        scheduleOverride -> scheduleOverride,
                        (left, right) -> left
                ));
        
        Set<UUID> appliedOverrideIds = new HashSet<>();
        List<ResolvedLessonResponse> resolvedLessons = new ArrayList<>();
        
        for (ScheduleTemplate scheduleTemplate : templates) {
            AcademicSemester semester = semestersById.get(scheduleTemplate.getSemesterId());
            if (semester == null) {
                continue;
            }
            
            LocalDate rangeStart = max(dateFrom, semester.getStartDate(), semester.getWeekOneStartDate());
            LocalDate rangeEnd = min(dateTo, semester.getEndDate());
            if (rangeStart.isAfter(rangeEnd)) {
                continue;
            }
            
            LocalDate occurrenceDate = rangeStart.with(TemporalAdjusters.nextOrSame(scheduleTemplate.getDayOfWeek()));
            while (!occurrenceDate.isAfter(rangeEnd)) {
                if (AcademicWeekSupport.matchesWeekType(semester, occurrenceDate, scheduleTemplate.getWeekType())) {
                    ScheduleOverride scheduleOverride = templateOverrides.get(
                            new OccurrenceKey(scheduleTemplate.getId(), occurrenceDate)
                    );
                    if (scheduleOverride == null) {
                        resolvedLessons.add(toResolvedLesson(scheduleTemplate, semester, occurrenceDate));
                    } else if (scheduleOverride.getOverrideType() == OverrideType.REPLACE) {
                        resolvedLessons.add(toResolvedLesson(scheduleOverride, semester));
                        appliedOverrideIds.add(scheduleOverride.getId());
                    } else if (scheduleOverride.getOverrideType() == OverrideType.CANCEL) {
                        appliedOverrideIds.add(scheduleOverride.getId());
                    }
                }
                occurrenceDate = occurrenceDate.plusWeeks(1);
            }
        }
        
        for (ScheduleOverride scheduleOverride : overrides) {
            if (scheduleOverride.getOverrideType() == OverrideType.CANCEL || appliedOverrideIds.contains(scheduleOverride.getId())) {
                continue;
            }
            
            AcademicSemester semester = semestersById.get(scheduleOverride.getSemesterId());
            if (semester == null) {
                continue;
            }
            if (!AcademicWeekSupport.isWithinSemester(semester, scheduleOverride.getDate())
                    || !AcademicWeekSupport.isOnOrAfterWeekOne(semester, scheduleOverride.getDate())) {
                continue;
            }
            
            resolvedLessons.add(toResolvedLesson(scheduleOverride, semester));
        }
        
        Map<UUID, Integer> slotNumbers = slotNumbers(resolvedLessons.stream().map(ResolvedLessonResponse::slotId).toList());
        
        return resolvedLessons.stream()
                .filter(response -> groupId == null || groupId.equals(response.groupId()))
                .filter(response -> teacherId == null || teacherId.equals(response.teacherId()))
                .filter(response -> roomId == null || roomId.equals(response.roomId()))
                .filter(response -> lessonType == null || lessonType == response.lessonType())
                .sorted(Comparator
                        .comparing(ResolvedLessonResponse::date)
                        .thenComparing(response -> slotNumbers.getOrDefault(response.slotId(), Integer.MAX_VALUE))
                        .thenComparing(response -> response.teacherId().toString())
                        .thenComparing(response -> response.groupId().toString()))
                .toList();
    }
    
    private ResolvedLessonResponse toResolvedLesson(
            ScheduleTemplate scheduleTemplate,
            AcademicSemester semester,
            LocalDate date
    ) {
        return new ResolvedLessonResponse(
                date,
                scheduleTemplate.getSemesterId(),
                scheduleTemplate.getId(),
                scheduleTemplate.getGroupId(),
                scheduleTemplate.getSubjectId(),
                scheduleTemplate.getTeacherId(),
                scheduleTemplate.getSlotId(),
                AcademicWeekSupport.weekNumber(semester, date),
                AcademicWeekSupport.resolvedWeekType(semester, date),
                scheduleTemplate.getLessonType(),
                displayName(scheduleTemplate.getLessonType()),
                scheduleTemplate.getLessonFormat(),
                scheduleTemplate.getRoomId(),
                scheduleTemplate.getOnlineMeetingUrl(),
                scheduleTemplate.getNotes(),
                ResolvedLessonSourceType.TEMPLATE,
                null
        );
    }
    
    private ResolvedLessonResponse toResolvedLesson(ScheduleOverride scheduleOverride, AcademicSemester semester) {
        return new ResolvedLessonResponse(
                scheduleOverride.getDate(),
                scheduleOverride.getSemesterId(),
                scheduleOverride.getTemplateId(),
                scheduleOverride.getGroupId(),
                scheduleOverride.getSubjectId(),
                scheduleOverride.getTeacherId(),
                scheduleOverride.getSlotId(),
                AcademicWeekSupport.weekNumber(semester, scheduleOverride.getDate()),
                AcademicWeekSupport.resolvedWeekType(semester, scheduleOverride.getDate()),
                scheduleOverride.getLessonType(),
                displayName(scheduleOverride.getLessonType()),
                scheduleOverride.getLessonFormat(),
                scheduleOverride.getRoomId(),
                scheduleOverride.getOnlineMeetingUrl(),
                scheduleOverride.getNotes(),
                ResolvedLessonSourceType.OVERRIDE,
                scheduleOverride.getOverrideType()
        );
    }
    
    private void validateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateTo.isBefore(dateFrom)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("dateFrom", dateFrom.toString());
            details.put("dateTo", dateTo.toString());
            
            throw new ScheduleValidationException(
                    "INVALID_DATE_RANGE",
                    "dateTo must be on or after dateFrom",
                    details
            );
        }
    }
    
    private Map<UUID, Integer> slotNumbers(Collection<UUID> slotIds) {
        return lessonSlotRepository.findAllById(slotIds).stream()
                .collect(Collectors.toMap(LessonSlot::getId, LessonSlot::getNumber));
    }
    
    private String displayName(LessonType lessonType) {
        return lessonType == null ? null : lessonType.getDisplayName();
    }
    
    private LocalDate max(LocalDate first, LocalDate second, LocalDate third) {
        return max(max(first, second), third);
    }
    
    private LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }
    
    private LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
    
    private record OccurrenceKey(UUID templateId, LocalDate date) {
    }
}

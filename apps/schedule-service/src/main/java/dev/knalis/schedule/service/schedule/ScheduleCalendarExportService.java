package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.dto.response.ResolvedLessonResponse;
import dev.knalis.schedule.entity.LessonSlot;
import dev.knalis.schedule.exception.ActiveAcademicSemesterNotFoundException;
import dev.knalis.schedule.exception.ScheduleValidationException;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleCalendarExportService {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private final AcademicSemesterRepository academicSemesterRepository;
    private final LessonSlotRepository lessonSlotRepository;
    private final ScheduleReadService scheduleReadService;
    
    @Transactional(readOnly = true)
    public String exportGroup(UUID groupId, LocalDate dateFrom, LocalDate dateTo) {
        DateRange dateRange = resolveDateRange(dateFrom, dateTo);
        return toCalendar(scheduleReadService.getGroupRange(groupId, dateRange.dateFrom(), dateRange.dateTo()));
    }
    
    @Transactional(readOnly = true)
    public String exportTeacher(UUID teacherId, LocalDate dateFrom, LocalDate dateTo) {
        DateRange dateRange = resolveDateRange(dateFrom, dateTo);
        return toCalendar(scheduleReadService.getTeacherRange(teacherId, dateRange.dateFrom(), dateRange.dateTo()));
    }
    
    public String toCalendar(List<ResolvedLessonResponse> lessons) {
        Map<UUID, LessonSlot> lessonSlots = lessonSlotRepository.findAllById(
                lessons.stream().map(ResolvedLessonResponse::slotId).toList()
        ).stream().collect(Collectors.toMap(LessonSlot::getId, lessonSlot -> lessonSlot));

        StringBuilder calendar = new StringBuilder();
        calendar.append("BEGIN:VCALENDAR\r\n");
        calendar.append("VERSION:2.0\r\n");
        calendar.append("PRODID:-//Studium//Schedule//EN\r\n");
        calendar.append("CALSCALE:GREGORIAN\r\n");
        
        for (ResolvedLessonResponse lesson : lessons) {
            LessonSlot lessonSlot = lessonSlots.get(lesson.slotId());
            if (lessonSlot == null) {
                continue;
            }
            LocalDateTime start = LocalDateTime.of(lesson.date(), lessonSlot.getStartTime());
            LocalDateTime end = LocalDateTime.of(lesson.date(), lessonSlot.getEndTime());
            
            calendar.append("BEGIN:VEVENT\r\n");
            calendar.append("UID:").append(uid(lesson)).append("\r\n");
            calendar.append("DTSTAMP:").append(format(LocalDateTime.now())).append("\r\n");
            calendar.append("DTSTART:").append(format(start)).append("\r\n");
            calendar.append("DTEND:").append(format(end)).append("\r\n");
            calendar.append("SUMMARY:").append(escape(summary(lesson))).append("\r\n");
            calendar.append("DESCRIPTION:").append(escape(description(lesson))).append("\r\n");
            if (lesson.roomId() != null) {
                calendar.append("LOCATION:").append(lesson.roomId()).append("\r\n");
            }
            calendar.append("END:VEVENT\r\n");
        }
        
        calendar.append("END:VCALENDAR\r\n");
        return calendar.toString();
    }

    private DateRange resolveDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null) {
            validateRange(dateFrom, dateTo);
            return new DateRange(dateFrom, dateTo);
        }

        var activeSemester = academicSemesterRepository.findFirstByActiveTrueOrderByStartDateDesc()
                .orElseThrow(ActiveAcademicSemesterNotFoundException::new);

        LocalDate resolvedDateFrom = dateFrom != null ? dateFrom : activeSemester.getStartDate();
        LocalDate resolvedDateTo = dateTo != null ? dateTo : activeSemester.getEndDate();
        validateRange(resolvedDateFrom, resolvedDateTo);
        return new DateRange(resolvedDateFrom, resolvedDateTo);
    }
    
    private String summary(ResolvedLessonResponse lesson) {
        String lessonType = lesson.lessonTypeDisplayName() == null ? "Lesson" : lesson.lessonTypeDisplayName();
        return lessonType + " - subject " + lesson.subjectId();
    }
    
    private String description(ResolvedLessonResponse lesson) {
        StringBuilder description = new StringBuilder();
        description.append("Lesson type: ").append(lesson.lessonType());
        description.append("\\nLesson format: ").append(lesson.lessonFormat());
        description.append("\\nSubject ID: ").append(lesson.subjectId());
        if (lesson.roomId() != null) {
            description.append("\\nRoom ID: ").append(lesson.roomId());
        }
        if (lesson.onlineMeetingUrl() != null) {
            description.append("\\nMeeting URL: ").append(lesson.onlineMeetingUrl());
        }
        if (lesson.notes() != null) {
            description.append("\\nNotes: ").append(lesson.notes());
        }
        return description.toString();
    }
    
    private String format(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneOffset.UTC).format(FORMATTER);
    }
    
    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }

    private void validateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateTo.isBefore(dateFrom)) {
            throw new ScheduleValidationException(
                    "INVALID_DATE_RANGE",
                    "dateTo must be on or after dateFrom"
            );
        }
    }

    private String uid(ResolvedLessonResponse lesson) {
        return lesson.date()
                + "-"
                + lesson.groupId()
                + "-"
                + lesson.subjectId()
                + "-"
                + lesson.slotId()
                + "-"
                + lesson.sourceType()
                + "@studium";
    }

    private record DateRange(LocalDate dateFrom, LocalDate dateTo) {
    }
}

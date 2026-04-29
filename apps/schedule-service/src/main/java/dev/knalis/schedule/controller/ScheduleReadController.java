package dev.knalis.schedule.controller;

import dev.knalis.schedule.dto.response.ResolvedLessonResponse;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.service.schedule.ScheduleCalendarExportService;
import dev.knalis.schedule.service.schedule.ScheduleReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedule")
@RequiredArgsConstructor
public class ScheduleReadController {
    
    private final ScheduleReadService scheduleReadService;
    private final ScheduleCalendarExportService scheduleCalendarExportService;
    
    @GetMapping("/groups/{groupId}/week")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<ResolvedLessonResponse> getGroupWeek(
            @PathVariable UUID groupId,
            @RequestParam LocalDate startDate
    ) {
        return scheduleReadService.getGroupWeek(groupId, startDate);
    }
    
    @GetMapping("/groups/{groupId}/range")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<ResolvedLessonResponse> getGroupRange(
            @PathVariable UUID groupId,
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo
    ) {
        return scheduleReadService.getGroupRange(groupId, dateFrom, dateTo);
    }

    @GetMapping(value = "/groups/{groupId}/export.ics", produces = "text/calendar")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<String> exportGroupCalendar(
            @PathVariable UUID groupId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo
    ) {
        return calendarResponse(
                "group-" + groupId + "-schedule.ics",
                scheduleCalendarExportService.exportGroup(groupId, dateFrom, dateTo)
        );
    }
    
    @GetMapping("/teachers/{teacherId}/week")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<ResolvedLessonResponse> getTeacherWeek(
            @PathVariable UUID teacherId,
            @RequestParam LocalDate startDate
    ) {
        return scheduleReadService.getTeacherWeek(teacherId, startDate);
    }
    
    @GetMapping("/teachers/{teacherId}/range")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<ResolvedLessonResponse> getTeacherRange(
            @PathVariable UUID teacherId,
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo
    ) {
        return scheduleReadService.getTeacherRange(teacherId, dateFrom, dateTo);
    }

    @GetMapping(value = "/teachers/{teacherId}/export.ics", produces = "text/calendar")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<String> exportTeacherCalendar(
            @PathVariable UUID teacherId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo
    ) {
        return calendarResponse(
                "teacher-" + teacherId + "-schedule.ics",
                scheduleCalendarExportService.exportTeacher(teacherId, dateFrom, dateTo)
        );
    }
    
    @GetMapping("/rooms/{roomId}/week")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<ResolvedLessonResponse> getRoomWeek(
            @PathVariable UUID roomId,
            @RequestParam LocalDate startDate
    ) {
        return scheduleReadService.getRoomWeek(roomId, startDate);
    }
    
    @GetMapping("/rooms/{roomId}/range")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<ResolvedLessonResponse> getRoomRange(
            @PathVariable UUID roomId,
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo
    ) {
        return scheduleReadService.getRoomRange(roomId, dateFrom, dateTo);
    }
    
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<ResolvedLessonResponse> search(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) LessonType lessonType,
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo
    ) {
        return scheduleReadService.search(groupId, teacherId, roomId, lessonType, dateFrom, dateTo);
    }

    private ResponseEntity<String> calendarResponse(String filename, String calendar) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString()
                )
                .body(calendar);
    }
}

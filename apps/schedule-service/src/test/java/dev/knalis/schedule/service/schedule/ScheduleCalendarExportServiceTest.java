package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.dto.response.ResolvedLessonResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonSlot;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.ResolvedLessonSourceType;
import dev.knalis.schedule.entity.WeekType;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleCalendarExportServiceTest {

    @Mock
    private AcademicSemesterRepository academicSemesterRepository;

    @Mock
    private LessonSlotRepository lessonSlotRepository;

    @Mock
    private ScheduleReadService scheduleReadService;

    private ScheduleCalendarExportService scheduleCalendarExportService;

    @BeforeEach
    void setUp() {
        scheduleCalendarExportService = new ScheduleCalendarExportService(
                academicSemesterRepository,
                lessonSlotRepository,
                scheduleReadService
        );
    }

    @Test
    void exportGroupUsesActiveSemesterAndRealSlotTimes() {
        UUID groupId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        AcademicSemester semester = new AcademicSemester();
        semester.setId(UUID.randomUUID());
        semester.setName("Autumn 2026");
        semester.setStartDate(LocalDate.of(2026, 9, 1));
        semester.setEndDate(LocalDate.of(2026, 12, 31));
        semester.setWeekOneStartDate(LocalDate.of(2026, 9, 7));
        semester.setActive(true);
        semester.setCreatedAt(Instant.now());
        semester.setUpdatedAt(Instant.now());

        LessonSlot lessonSlot = new LessonSlot();
        lessonSlot.setId(slotId);
        lessonSlot.setNumber(1);
        lessonSlot.setStartTime(LocalTime.of(8, 30));
        lessonSlot.setEndTime(LocalTime.of(10, 0));
        lessonSlot.setActive(true);

        ResolvedLessonResponse lesson = new ResolvedLessonResponse(
                LocalDate.of(2026, 9, 8),
                semester.getId(),
                UUID.randomUUID(),
                groupId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                slotId,
                1,
                WeekType.ODD,
                LessonType.LECTURE,
                "Lecture",
                LessonFormat.OFFLINE,
                roomId,
                null,
                "Campus lesson",
                ResolvedLessonSourceType.TEMPLATE,
                null
        );

        when(academicSemesterRepository.findFirstByActiveTrueOrderByStartDateDesc()).thenReturn(Optional.of(semester));
        when(scheduleReadService.getGroupRange(groupId, semester.getStartDate(), semester.getEndDate()))
                .thenReturn(List.of(lesson));
        when(lessonSlotRepository.findAllById(List.of(slotId))).thenReturn(List.of(lessonSlot));

        String calendar = scheduleCalendarExportService.exportGroup(groupId, null, null);

        assertTrue(calendar.contains("BEGIN:VCALENDAR"));
        assertTrue(calendar.contains("DTSTART:20260908T083000Z"));
        assertTrue(calendar.contains("DTEND:20260908T100000Z"));
        assertTrue(calendar.contains("SUMMARY:Lecture - subject " + lesson.subjectId()));
        assertTrue(calendar.contains("LOCATION:" + roomId));
    }
}

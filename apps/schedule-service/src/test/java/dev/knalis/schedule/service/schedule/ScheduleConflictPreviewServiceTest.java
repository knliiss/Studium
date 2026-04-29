package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.dto.request.ScheduleConflictCheckRequest;
import dev.knalis.schedule.dto.response.ScheduleConflictCheckResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.WeekType;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleConflictPreviewServiceTest {

    @Mock
    private AcademicSemesterRepository academicSemesterRepository;

    @Mock
    private ScheduleConflictService scheduleConflictService;

    private ScheduleConflictPreviewService scheduleConflictPreviewService;

    @BeforeEach
    void setUp() {
        scheduleConflictPreviewService = new ScheduleConflictPreviewService(
                academicSemesterRepository,
                scheduleConflictService
        );
    }

    @Test
    void checkReturnsNoConflictsForTemplateCandidate() {
        UUID semesterId = UUID.randomUUID();
        AcademicSemester semester = new AcademicSemester();
        semester.setId(semesterId);

        when(academicSemesterRepository.findById(semesterId)).thenReturn(Optional.of(semester));

        ScheduleConflictCheckResponse response = scheduleConflictPreviewService.check(new ScheduleConflictCheckRequest(
                semesterId,
                null,
                null,
                UUID.randomUUID(),
                null,
                DayOfWeek.MONDAY,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                WeekType.ALL,
                LessonType.LECTURE,
                LessonFormat.OFFLINE,
                null
        ));

        assertFalse(response.hasConflicts());
        assertEquals(List.of(), response.conflicts());
    }

    @Test
    void checkMapsOverrideConflictToFrontendResponse() {
        UUID semesterId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID conflictingTemplateId = UUID.randomUUID();

        AcademicSemester semester = new AcademicSemester();
        semester.setId(semesterId);

        when(academicSemesterRepository.findById(semesterId)).thenReturn(Optional.of(semester));
        doThrow(new ScheduleConflictException(
                "ROOM_SCHEDULE_CONFLICT",
                "Room is already used on the selected date and slot",
                Map.of(
                        "conflictType", "ROOM",
                        "existingTemplateId", conflictingTemplateId,
                        "slotId", slotId
                )
        )).when(scheduleConflictService).assertNoOverrideConflicts(
                any(),
                eq(semester),
                eq(overrideId),
                eq(templateId)
        );

        ScheduleConflictCheckResponse response = scheduleConflictPreviewService.check(new ScheduleConflictCheckRequest(
                semesterId,
                templateId,
                overrideId,
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 8),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                slotId,
                null,
                LessonType.LABORATORY,
                LessonFormat.OFFLINE,
                OverrideType.REPLACE
        ));

        assertTrue(response.hasConflicts());
        assertEquals(1, response.conflicts().size());
        assertEquals("ROOM_CONFLICT", response.conflicts().getFirst().type());
        assertEquals(conflictingTemplateId, response.conflicts().getFirst().conflictingEntityId());
        assertEquals("SCHEDULE_TEMPLATE", response.conflicts().getFirst().conflictingEntityType());
        assertEquals(LocalDate.of(2026, 9, 8), response.conflicts().getFirst().date());
        assertEquals(slotId, response.conflicts().getFirst().slotId());

        verify(scheduleConflictService).assertNoOverrideConflicts(
                any(),
                eq(semester),
                eq(overrideId),
                eq(templateId)
        );
    }
}

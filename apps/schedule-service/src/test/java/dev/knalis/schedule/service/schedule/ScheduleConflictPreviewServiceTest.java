package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.dto.request.ScheduleConflictCheckRequest;
import dev.knalis.schedule.dto.response.ScheduleConflictCheckResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonSlot;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.Room;
import dev.knalis.schedule.entity.Subgroup;
import dev.knalis.schedule.entity.WeekType;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import dev.knalis.schedule.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private LessonSlotRepository lessonSlotRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleConflictService scheduleConflictService;

    private ScheduleConflictPreviewService scheduleConflictPreviewService;

    @BeforeEach
    void setUp() {
        scheduleConflictPreviewService = new ScheduleConflictPreviewService(
                academicSemesterRepository,
                lessonSlotRepository,
                roomRepository,
                scheduleConflictService
        );
    }

    @Test
    void checkReturnsNoConflictsForTemplateCandidate() {
        UUID semesterId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AcademicSemester semester = new AcademicSemester();
        semester.setId(semesterId);
        LessonSlot lessonSlot = canonicalSlot(slotId, 1);
        Room room = activeRoom(roomId);

        when(academicSemesterRepository.findById(semesterId)).thenReturn(Optional.of(semester));
        when(lessonSlotRepository.findById(slotId)).thenReturn(Optional.of(lessonSlot));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        ScheduleConflictCheckResponse response = scheduleConflictPreviewService.check(new ScheduleConflictCheckRequest(
                semesterId,
                null,
                null,
                UUID.randomUUID(),
                null,
                DayOfWeek.MONDAY,
                UUID.randomUUID(),
                UUID.randomUUID(),
                roomId,
                "https://meet.studium.local/conflict-check",
                slotId,
                WeekType.ALL,
                Subgroup.FIRST,
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
        UUID roomId = UUID.randomUUID();
        UUID conflictingTemplateId = UUID.randomUUID();

        AcademicSemester semester = new AcademicSemester();
        semester.setId(semesterId);
        LessonSlot lessonSlot = canonicalSlot(slotId, 1);
        Room room = activeRoom(roomId);

        when(academicSemesterRepository.findById(semesterId)).thenReturn(Optional.of(semester));
        when(lessonSlotRepository.findById(slotId)).thenReturn(Optional.of(lessonSlot));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        doThrow(new ScheduleConflictException(
                "ROOM_SCHEDULE_CONFLICT",
                "Room is already used on the selected date and slot",
                Map.of(
                        "conflictType", "ROOM",
                        "existingTemplateId", conflictingTemplateId,
                        "slotId", slotId,
                        "groupId", UUID.randomUUID(),
                        "subgroup", "SECOND",
                        "teacherId", UUID.randomUUID(),
                        "roomId", roomId
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
                roomId,
                "https://meet.studium.local/replacement",
                slotId,
                null,
                Subgroup.SECOND,
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

    private LessonSlot canonicalSlot(UUID slotId, int number) {
        LessonSlot lessonSlot = new LessonSlot();
        lessonSlot.setId(slotId);
        lessonSlot.setNumber(number);
        lessonSlot.setStartTime(LocalTime.of(8, 30));
        lessonSlot.setEndTime(LocalTime.of(9, 50));
        lessonSlot.setActive(true);
        return lessonSlot;
    }

    private Room activeRoom(UUID roomId) {
        Room room = new Room();
        room.setId(roomId);
        room.setCode("A-101");
        room.setActive(true);
        return room;
    }
}

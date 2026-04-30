package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.dto.response.ResolvedLessonResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.LessonSlot;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.ResolvedLessonSourceType;
import dev.knalis.schedule.entity.ScheduleOverride;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.Subgroup;
import dev.knalis.schedule.entity.WeekType;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import dev.knalis.schedule.repository.ScheduleOverrideRepository;
import dev.knalis.schedule.repository.ScheduleTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleReadServiceTest {

    @Mock
    private AcademicSemesterRepository academicSemesterRepository;

    @Mock
    private ScheduleTemplateRepository scheduleTemplateRepository;

    @Mock
    private ScheduleOverrideRepository scheduleOverrideRepository;

    @Mock
    private LessonSlotRepository lessonSlotRepository;

    private ScheduleReadService scheduleReadService;

    @BeforeEach
    void setUp() {
        scheduleReadService = new ScheduleReadService(
                academicSemesterRepository,
                scheduleTemplateRepository,
                scheduleOverrideRepository,
                lessonSlotRepository,
                Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void getGroupRangeAppliesReplaceCancelAndExtraOverrides() {
        UUID semesterId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        AcademicSemester semester = new AcademicSemester();
        semester.setId(semesterId);
        semester.setName("Autumn 2026");
        semester.setStartDate(LocalDate.of(2026, 9, 1));
        semester.setEndDate(LocalDate.of(2026, 12, 31));
        semester.setWeekOneStartDate(LocalDate.of(2026, 9, 7));

        LessonSlot lessonSlot = new LessonSlot();
        lessonSlot.setId(slotId);
        lessonSlot.setNumber(1);
        lessonSlot.setStartTime(LocalTime.of(8, 30));
        lessonSlot.setEndTime(LocalTime.of(9, 50));
        lessonSlot.setActive(true);

        ScheduleTemplate scheduleTemplate = new ScheduleTemplate();
        scheduleTemplate.setId(UUID.randomUUID());
        scheduleTemplate.setSemesterId(semesterId);
        scheduleTemplate.setGroupId(groupId);
        scheduleTemplate.setSubjectId(subjectId);
        scheduleTemplate.setTeacherId(teacherId);
        scheduleTemplate.setDayOfWeek(DayOfWeek.MONDAY);
        scheduleTemplate.setSlotId(slotId);
        scheduleTemplate.setWeekType(WeekType.ALL);
        scheduleTemplate.setSubgroup(Subgroup.ALL);
        scheduleTemplate.setLessonType(LessonType.LECTURE);
        scheduleTemplate.setLessonFormat(LessonFormat.OFFLINE);
        scheduleTemplate.setRoomId(UUID.randomUUID());
        scheduleTemplate.setNotes("Lecture");
        scheduleTemplate.setActive(true);

        ScheduleOverride replaceOverride = new ScheduleOverride();
        replaceOverride.setId(UUID.randomUUID());
        replaceOverride.setSemesterId(semesterId);
        replaceOverride.setTemplateId(scheduleTemplate.getId());
        replaceOverride.setOverrideType(OverrideType.REPLACE);
        replaceOverride.setDate(LocalDate.of(2026, 9, 7));
        replaceOverride.setGroupId(groupId);
        replaceOverride.setSubjectId(subjectId);
        replaceOverride.setTeacherId(teacherId);
        replaceOverride.setSlotId(slotId);
        replaceOverride.setSubgroup(Subgroup.ALL);
        replaceOverride.setLessonType(LessonType.PRACTICAL);
        replaceOverride.setLessonFormat(LessonFormat.ONLINE);
        replaceOverride.setNotes("Replaced");

        ScheduleOverride cancelOverride = new ScheduleOverride();
        cancelOverride.setId(UUID.randomUUID());
        cancelOverride.setSemesterId(semesterId);
        cancelOverride.setTemplateId(scheduleTemplate.getId());
        cancelOverride.setOverrideType(OverrideType.CANCEL);
        cancelOverride.setDate(LocalDate.of(2026, 9, 14));
        cancelOverride.setGroupId(groupId);
        cancelOverride.setSubjectId(subjectId);
        cancelOverride.setTeacherId(teacherId);
        cancelOverride.setSlotId(slotId);
        cancelOverride.setSubgroup(Subgroup.ALL);
        cancelOverride.setLessonType(LessonType.LECTURE);
        cancelOverride.setLessonFormat(LessonFormat.OFFLINE);

        ScheduleOverride extraOverride = new ScheduleOverride();
        extraOverride.setId(UUID.randomUUID());
        extraOverride.setSemesterId(semesterId);
        extraOverride.setOverrideType(OverrideType.EXTRA);
        extraOverride.setDate(LocalDate.of(2026, 9, 9));
        extraOverride.setGroupId(groupId);
        extraOverride.setSubjectId(subjectId);
        extraOverride.setTeacherId(teacherId);
        extraOverride.setSlotId(slotId);
        extraOverride.setSubgroup(Subgroup.ALL);
        extraOverride.setLessonType(LessonType.LABORATORY);
        extraOverride.setLessonFormat(LessonFormat.ONLINE);
        extraOverride.setNotes("Extra");

        when(academicSemesterRepository.findAllOverlapping(
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 15)
        )).thenReturn(List.of(semester));
        when(scheduleTemplateRepository.findAllBySemesterIdInAndActiveTrue(List.of(semesterId)))
                .thenReturn(List.of(scheduleTemplate));
        when(scheduleOverrideRepository.findAllBySemesterIdInAndDateBetween(
                List.of(semesterId),
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 15)
        )).thenReturn(List.of(replaceOverride, cancelOverride, extraOverride));
        when(lessonSlotRepository.findAllById(List.of(slotId, slotId))).thenReturn(List.of(lessonSlot));

        List<ResolvedLessonResponse> result = scheduleReadService.getGroupRange(
                groupId,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 15)
        );

        assertEquals(2, result.size());
        assertEquals(LocalDate.of(2026, 9, 7), result.get(0).date());
        assertEquals(ResolvedLessonSourceType.OVERRIDE, result.get(0).sourceType());
        assertEquals(OverrideType.REPLACE, result.get(0).overrideType());
        assertEquals(LessonType.PRACTICAL, result.get(0).lessonType());
        assertEquals("Practical", result.get(0).lessonTypeDisplayName());
        assertEquals(LocalDate.of(2026, 9, 9), result.get(1).date());
        assertEquals(ResolvedLessonSourceType.OVERRIDE, result.get(1).sourceType());
        assertEquals(OverrideType.EXTRA, result.get(1).overrideType());
        assertEquals(LessonType.LABORATORY, result.get(1).lessonType());
        assertEquals("Laboratory", result.get(1).lessonTypeDisplayName());
    }
}

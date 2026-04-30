package dev.knalis.schedule.service.override;

import dev.knalis.schedule.dto.request.CreateScheduleOverrideRequest;
import dev.knalis.schedule.dto.response.ScheduleOverrideResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.ScheduleOverride;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.Subgroup;
import dev.knalis.schedule.entity.TeacherDebt;
import dev.knalis.schedule.entity.TeacherDebtStatus;
import dev.knalis.schedule.entity.WeekType;
import dev.knalis.schedule.exception.ScheduleAccessDeniedException;
import dev.knalis.schedule.factory.debt.TeacherDebtFactory;
import dev.knalis.schedule.factory.override.ScheduleOverrideFactory;
import dev.knalis.schedule.mapper.ScheduleOverrideMapper;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import dev.knalis.schedule.repository.RoomRepository;
import dev.knalis.schedule.repository.ScheduleOverrideRepository;
import dev.knalis.schedule.repository.ScheduleTemplateRepository;
import dev.knalis.schedule.repository.TeacherDebtRepository;
import dev.knalis.schedule.service.common.ScheduleAuditService;
import dev.knalis.schedule.service.common.ScheduleEventPublisher;
import dev.knalis.schedule.service.schedule.ScheduleConflictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleOverrideServiceTest {

    @Mock
    private ScheduleOverrideRepository scheduleOverrideRepository;

    @Mock
    private ScheduleTemplateRepository scheduleTemplateRepository;

    @Mock
    private TeacherDebtRepository teacherDebtRepository;

    @Mock
    private AcademicSemesterRepository academicSemesterRepository;

    @Mock
    private LessonSlotRepository lessonSlotRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleOverrideMapper scheduleOverrideMapper;

    @Mock
    private ScheduleConflictService scheduleConflictService;

    @Mock
    private ScheduleEventPublisher scheduleEventPublisher;

    @Mock
    private ScheduleAuditService scheduleAuditService;

    private ScheduleOverrideService scheduleOverrideService;

    @BeforeEach
    void setUp() {
        scheduleOverrideService = new ScheduleOverrideService(
                scheduleOverrideRepository,
                scheduleTemplateRepository,
                teacherDebtRepository,
                academicSemesterRepository,
                lessonSlotRepository,
                roomRepository,
                new TeacherDebtFactory(),
                new ScheduleOverrideFactory(),
                scheduleOverrideMapper,
                scheduleConflictService,
                scheduleAuditService,
                scheduleEventPublisher
        );
    }

    @Test
    void createOverrideThrowsWhenTeacherChangesAnotherTeachersLesson() {
        UUID actorId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 7);

        ScheduleTemplate scheduleTemplate = new ScheduleTemplate();
        scheduleTemplate.setId(templateId);
        scheduleTemplate.setSemesterId(semesterId);
        scheduleTemplate.setGroupId(UUID.randomUUID());
        scheduleTemplate.setSubjectId(UUID.randomUUID());
        scheduleTemplate.setTeacherId(teacherId);
        scheduleTemplate.setDayOfWeek(DayOfWeek.MONDAY);
        scheduleTemplate.setSlotId(UUID.randomUUID());
        scheduleTemplate.setWeekType(WeekType.ALL);
        scheduleTemplate.setSubgroup(Subgroup.ALL);
        scheduleTemplate.setLessonType(LessonType.LECTURE);
        scheduleTemplate.setLessonFormat(LessonFormat.ONLINE);
        scheduleTemplate.setActive(true);

        AcademicSemester semester = new AcademicSemester();
        semester.setId(semesterId);
        semester.setName("Autumn 2026");
        semester.setStartDate(LocalDate.of(2026, 9, 1));
        semester.setEndDate(LocalDate.of(2026, 12, 31));
        semester.setWeekOneStartDate(LocalDate.of(2026, 9, 7));

        when(scheduleTemplateRepository.findById(templateId)).thenReturn(Optional.of(scheduleTemplate));
        when(academicSemesterRepository.findById(semesterId)).thenReturn(Optional.of(semester));
        doNothing().when(scheduleConflictService).assertTemplateOccurrenceExists(scheduleTemplate, semester, date);
        doNothing().when(scheduleConflictService).assertNoDuplicateTemplateOverride(templateId, date, null);

        assertThrows(
                ScheduleAccessDeniedException.class,
                () -> scheduleOverrideService.createOverride(
                        actorId,
                        false,
                        new CreateScheduleOverrideRequest(
                                null,
                                templateId,
                                OverrideType.CANCEL,
                                date,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "Teacher cannot cancel someone else's lesson"
                        )
                )
        );
    }

    @Test
    void createCancelOverrideCreatesOpenTeacherDebt() {
        UUID teacherId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 7);

        ScheduleTemplate scheduleTemplate = new ScheduleTemplate();
        scheduleTemplate.setId(templateId);
        scheduleTemplate.setSemesterId(semesterId);
        scheduleTemplate.setGroupId(groupId);
        scheduleTemplate.setSubjectId(subjectId);
        scheduleTemplate.setTeacherId(teacherId);
        scheduleTemplate.setDayOfWeek(DayOfWeek.MONDAY);
        scheduleTemplate.setSlotId(slotId);
        scheduleTemplate.setWeekType(WeekType.ALL);
        scheduleTemplate.setSubgroup(Subgroup.FIRST);
        scheduleTemplate.setLessonType(LessonType.PRACTICAL);
        scheduleTemplate.setLessonFormat(LessonFormat.ONLINE);
        scheduleTemplate.setActive(true);

        AcademicSemester semester = new AcademicSemester();
        semester.setId(semesterId);
        semester.setName("Autumn 2026");
        semester.setStartDate(LocalDate.of(2026, 9, 1));
        semester.setEndDate(LocalDate.of(2026, 12, 31));
        semester.setWeekOneStartDate(LocalDate.of(2026, 9, 7));

        ScheduleOverrideResponse response = new ScheduleOverrideResponse(
                UUID.randomUUID(),
                semesterId,
                templateId,
                OverrideType.CANCEL,
                date,
                groupId,
                subjectId,
                teacherId,
                slotId,
                Subgroup.FIRST,
                LessonType.PRACTICAL,
                "Practical",
                LessonFormat.ONLINE,
                null,
                null,
                "Teacher is unavailable",
                teacherId,
                Instant.now(),
                Instant.now()
        );

        when(scheduleTemplateRepository.findById(templateId)).thenReturn(Optional.of(scheduleTemplate));
        when(academicSemesterRepository.findById(semesterId)).thenReturn(Optional.of(semester));
        doNothing().when(scheduleConflictService).assertTemplateOccurrenceExists(scheduleTemplate, semester, date);
        doNothing().when(scheduleConflictService).assertNoDuplicateTemplateOverride(templateId, date, null);
        when(scheduleOverrideRepository.save(any(ScheduleOverride.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleOverrideMapper.toResponse(any(ScheduleOverride.class))).thenReturn(response);

        scheduleOverrideService.createOverride(
                teacherId,
                false,
                new CreateScheduleOverrideRequest(
                        null,
                        templateId,
                        OverrideType.CANCEL,
                        date,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Teacher is unavailable"
                )
        );

        ArgumentCaptor<TeacherDebt> debtCaptor = ArgumentCaptor.forClass(TeacherDebt.class);
        verify(teacherDebtRepository).save(debtCaptor.capture());
        TeacherDebt teacherDebt = debtCaptor.getValue();
        assertEquals(teacherId, teacherDebt.getTeacherId());
        assertEquals(groupId, teacherDebt.getGroupId());
        assertEquals(subjectId, teacherDebt.getSubjectId());
        assertEquals(date, teacherDebt.getDate());
        assertEquals(slotId, teacherDebt.getSlotId());
        assertEquals(LessonType.PRACTICAL, teacherDebt.getLessonType());
        assertEquals(Subgroup.FIRST, teacherDebt.getSubgroup());
        assertEquals("Teacher is unavailable", teacherDebt.getReason());
        assertEquals(TeacherDebtStatus.OPEN, teacherDebt.getStatus());
    }
}

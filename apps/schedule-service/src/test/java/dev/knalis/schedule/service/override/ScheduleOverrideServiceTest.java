package dev.knalis.schedule.service.override;

import dev.knalis.schedule.dto.request.CreateScheduleOverrideRequest;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.WeekType;
import dev.knalis.schedule.exception.ScheduleAccessDeniedException;
import dev.knalis.schedule.factory.override.ScheduleOverrideFactory;
import dev.knalis.schedule.mapper.ScheduleOverrideMapper;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import dev.knalis.schedule.repository.RoomRepository;
import dev.knalis.schedule.repository.ScheduleOverrideRepository;
import dev.knalis.schedule.repository.ScheduleTemplateRepository;
import dev.knalis.schedule.service.common.ScheduleAuditService;
import dev.knalis.schedule.service.common.ScheduleEventPublisher;
import dev.knalis.schedule.service.schedule.ScheduleConflictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleOverrideServiceTest {
    
    @Mock
    private ScheduleOverrideRepository scheduleOverrideRepository;
    
    @Mock
    private ScheduleTemplateRepository scheduleTemplateRepository;
    
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
                academicSemesterRepository,
                lessonSlotRepository,
                roomRepository,
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
                                "Teacher cannot cancel someone else's lesson"
                        )
                )
        );
    }
}

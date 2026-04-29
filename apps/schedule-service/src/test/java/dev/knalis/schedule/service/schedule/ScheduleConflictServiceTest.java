package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.WeekType;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.repository.ScheduleOverrideRepository;
import dev.knalis.schedule.repository.ScheduleTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleConflictServiceTest {
    
    @Mock
    private ScheduleTemplateRepository scheduleTemplateRepository;
    
    @Mock
    private ScheduleOverrideRepository scheduleOverrideRepository;
    
    private ScheduleConflictService scheduleConflictService;
    
    @BeforeEach
    void setUp() {
        scheduleConflictService = new ScheduleConflictService(scheduleTemplateRepository, scheduleOverrideRepository);
    }
    
    @Test
    void assertNoTemplateConflictsThrowsForOverlappingWeekTypes() {
        UUID semesterId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        
        ScheduleTemplate existing = new ScheduleTemplate();
        existing.setId(UUID.randomUUID());
        existing.setSemesterId(semesterId);
        existing.setGroupId(groupId);
        existing.setSubjectId(UUID.randomUUID());
        existing.setTeacherId(UUID.randomUUID());
        existing.setDayOfWeek(DayOfWeek.MONDAY);
        existing.setSlotId(slotId);
        existing.setWeekType(WeekType.ALL);
        existing.setLessonType(LessonType.LECTURE);
        existing.setLessonFormat(LessonFormat.OFFLINE);
        existing.setRoomId(UUID.randomUUID());
        existing.setActive(true);
        
        ScheduleTemplate candidate = new ScheduleTemplate();
        candidate.setSemesterId(semesterId);
        candidate.setGroupId(groupId);
        candidate.setSubjectId(UUID.randomUUID());
        candidate.setTeacherId(UUID.randomUUID());
        candidate.setDayOfWeek(DayOfWeek.MONDAY);
        candidate.setSlotId(slotId);
        candidate.setWeekType(WeekType.ODD);
        candidate.setLessonType(LessonType.PRACTICAL);
        candidate.setLessonFormat(LessonFormat.ONLINE);
        candidate.setActive(true);
        
        when(scheduleTemplateRepository.findAllBySemesterIdAndDayOfWeekAndSlotIdAndActiveTrue(
                semesterId,
                DayOfWeek.MONDAY,
                slotId
        )).thenReturn(List.of(existing));
        
        assertThrows(
                ScheduleConflictException.class,
                () -> scheduleConflictService.assertNoTemplateConflicts(candidate, null, List.of())
        );
    }
}

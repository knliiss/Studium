package dev.knalis.schedule.service.template;

import dev.knalis.schedule.dto.response.ScheduleTemplateResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.ScheduleTemplateStatus;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.factory.template.ScheduleTemplateFactory;
import dev.knalis.schedule.mapper.ScheduleTemplateMapper;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import dev.knalis.schedule.repository.RoomRepository;
import dev.knalis.schedule.repository.ScheduleTemplateRepository;
import dev.knalis.schedule.service.common.ScheduleAuditService;
import dev.knalis.schedule.service.schedule.ScheduleConflictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleTemplateServiceTest {

    @Mock
    private ScheduleTemplateRepository scheduleTemplateRepository;

    @Mock
    private AcademicSemesterRepository academicSemesterRepository;

    @Mock
    private LessonSlotRepository lessonSlotRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleTemplateMapper scheduleTemplateMapper;

    @Mock
    private ScheduleConflictService scheduleConflictService;

    @Mock
    private ScheduleAuditService scheduleAuditService;

    private ScheduleTemplateService scheduleTemplateService;

    @BeforeEach
    void setUp() {
        scheduleTemplateService = new ScheduleTemplateService(
                scheduleTemplateRepository,
                academicSemesterRepository,
                lessonSlotRepository,
                roomRepository,
                new ScheduleTemplateFactory(),
                scheduleTemplateMapper,
                scheduleConflictService,
                scheduleAuditService
        );
    }

    @Test
    void deleteTemplateArchivesTemplate() {
        UUID templateId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ScheduleTemplate template = template(templateId, true, ScheduleTemplateStatus.ACTIVE);

        when(scheduleTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(scheduleTemplateMapper.toResponse(any(ScheduleTemplate.class))).thenAnswer(invocation -> response(invocation.getArgument(0)));
        when(scheduleTemplateRepository.save(any(ScheduleTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        scheduleTemplateService.deleteTemplate(actorId, templateId);

        ArgumentCaptor<ScheduleTemplate> captor = ArgumentCaptor.forClass(ScheduleTemplate.class);
        verify(scheduleTemplateRepository).save(captor.capture());
        assertEquals(ScheduleTemplateStatus.ARCHIVED, captor.getValue().getStatus());
        assertEquals(false, captor.getValue().isActive());
    }

    @Test
    void getTemplatesByGroupUsesActiveOnlyRepositoryMethod() {
        UUID groupId = UUID.randomUUID();
        ScheduleTemplate template = template(UUID.randomUUID(), true, ScheduleTemplateStatus.ACTIVE);

        when(scheduleTemplateRepository.findAllByGroupIdAndActiveTrueOrderByCreatedAtDesc(groupId))
                .thenReturn(List.of(template));
        when(lessonSlotRepository.findAllById(any())).thenReturn(List.of());
        when(scheduleTemplateMapper.toResponse(any(ScheduleTemplate.class))).thenAnswer(invocation -> response(invocation.getArgument(0)));

        List<ScheduleTemplateResponse> result = scheduleTemplateService.getTemplatesByGroup(groupId);

        verify(scheduleTemplateRepository).findAllByGroupIdAndActiveTrueOrderByCreatedAtDesc(groupId);
        verify(scheduleTemplateRepository, never()).findAllByGroupIdOrderByCreatedAtDesc(groupId);
        assertEquals(1, result.size());
    }

    @Test
    void getTemplatesBySemesterUsesActiveOnlyRepositoryMethod() {
        UUID semesterId = UUID.randomUUID();
        AcademicSemester semester = new AcademicSemester();
        semester.setId(semesterId);
        ScheduleTemplate template = template(UUID.randomUUID(), true, ScheduleTemplateStatus.ACTIVE);

        when(academicSemesterRepository.findById(semesterId)).thenReturn(Optional.of(semester));
        when(scheduleTemplateRepository.findAllBySemesterIdAndActiveTrueOrderByCreatedAtAsc(semesterId))
                .thenReturn(List.of(template));
        when(lessonSlotRepository.findAllById(any())).thenReturn(List.of());
        when(scheduleTemplateMapper.toResponse(any(ScheduleTemplate.class))).thenAnswer(invocation -> response(invocation.getArgument(0)));

        List<ScheduleTemplateResponse> result = scheduleTemplateService.getTemplatesBySemester(semesterId);

        verify(scheduleTemplateRepository).findAllBySemesterIdAndActiveTrueOrderByCreatedAtAsc(semesterId);
        verify(scheduleTemplateRepository, never()).findAllBySemesterIdOrderByCreatedAtAsc(semesterId);
        assertEquals(1, result.size());
    }

    @Test
    void restoreTemplateReactivatesArchivedTemplateWhenNoConflicts() {
        UUID templateId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ScheduleTemplate template = template(templateId, false, ScheduleTemplateStatus.ARCHIVED);

        when(scheduleTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(scheduleTemplateMapper.toResponse(any(ScheduleTemplate.class))).thenAnswer(invocation -> response(invocation.getArgument(0)));
        when(scheduleTemplateRepository.save(any(ScheduleTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleTemplateResponse response = scheduleTemplateService.restoreTemplate(actorId, templateId);

        verify(scheduleConflictService).assertNoTemplateConflicts(eq(template), eq(templateId), eq(List.<ScheduleTemplate>of()));
        assertEquals(true, response.active());
        assertEquals(ScheduleTemplateStatus.ACTIVE, response.status());
    }

    @Test
    void restoreTemplateThrowsConflictForNonArchivedTemplate() {
        UUID templateId = UUID.randomUUID();
        ScheduleTemplate template = template(templateId, true, ScheduleTemplateStatus.ACTIVE);
        when(scheduleTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        assertThrows(
                ScheduleConflictException.class,
                () -> scheduleTemplateService.restoreTemplate(UUID.randomUUID(), templateId)
        );

        verify(scheduleTemplateRepository, never()).save(any(ScheduleTemplate.class));
    }

    private ScheduleTemplate template(UUID id, boolean active, ScheduleTemplateStatus status) {
        ScheduleTemplate template = new ScheduleTemplate();
        template.setId(id);
        template.setSemesterId(UUID.randomUUID());
        template.setGroupId(UUID.randomUUID());
        template.setSubjectId(UUID.randomUUID());
        template.setTeacherId(UUID.randomUUID());
        template.setDayOfWeek(DayOfWeek.MONDAY);
        template.setSlotId(UUID.randomUUID());
        template.setActive(active);
        template.setStatus(status);
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }

    private ScheduleTemplateResponse response(ScheduleTemplate template) {
        return new ScheduleTemplateResponse(
                template.getId(),
                template.getSemesterId(),
                template.getGroupId(),
                template.getSubjectId(),
                template.getTeacherId(),
                template.getDayOfWeek(),
                template.getSlotId(),
                template.getWeekType(),
                template.getSubgroup(),
                template.getLessonType(),
                template.getLessonType() == null ? null : template.getLessonType().getDisplayName(),
                template.getLessonFormat(),
                template.getRoomId(),
                template.getOnlineMeetingUrl(),
                template.getNotes(),
                template.getStatus(),
                template.isActive(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}

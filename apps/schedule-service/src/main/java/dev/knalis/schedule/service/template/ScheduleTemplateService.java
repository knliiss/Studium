package dev.knalis.schedule.service.template;

import dev.knalis.schedule.dto.request.BulkCreateScheduleTemplatesRequest;
import dev.knalis.schedule.dto.request.CreateScheduleTemplateRequest;
import dev.knalis.schedule.dto.request.ImportScheduleTemplatesRequest;
import dev.knalis.schedule.dto.request.UpdateScheduleTemplateRequest;
import dev.knalis.schedule.dto.response.ScheduleConflictItemResponse;
import dev.knalis.schedule.dto.response.ScheduleTemplateResponse;
import dev.knalis.schedule.dto.response.ScheduleTemplateImportErrorResponse;
import dev.knalis.schedule.dto.response.ScheduleTemplateImportResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonSlot;
import dev.knalis.schedule.entity.Room;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.ScheduleTemplateStatus;
import dev.knalis.schedule.entity.Subgroup;
import dev.knalis.schedule.exception.AcademicSemesterNotFoundException;
import dev.knalis.schedule.exception.LessonSlotNotFoundException;
import dev.knalis.schedule.exception.RoomNotFoundException;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.exception.ScheduleTemplateNotFoundException;
import dev.knalis.schedule.exception.ScheduleValidationException;
import dev.knalis.schedule.factory.template.ScheduleTemplateFactory;
import dev.knalis.schedule.mapper.ScheduleTemplateMapper;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import dev.knalis.schedule.repository.RoomRepository;
import dev.knalis.schedule.repository.ScheduleTemplateRepository;
import dev.knalis.schedule.service.common.ScheduleAuditService;
import dev.knalis.schedule.service.schedule.ScheduleConflictService;
import dev.knalis.schedule.service.slot.CanonicalLessonSlots;
import dev.knalis.shared.web.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleTemplateService {

    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final AcademicSemesterRepository academicSemesterRepository;
    private final LessonSlotRepository lessonSlotRepository;
    private final RoomRepository roomRepository;
    private final ScheduleTemplateFactory scheduleTemplateFactory;
    private final ScheduleTemplateMapper scheduleTemplateMapper;
    private final ScheduleConflictService scheduleConflictService;
    private final ScheduleAuditService scheduleAuditService;

    @Transactional
    public ScheduleTemplateResponse createTemplate(UUID currentUserId, CreateScheduleTemplateRequest request) {
        ScheduleTemplate scheduleTemplate = buildTemplate(request);
        scheduleConflictService.assertNoTemplateConflicts(scheduleTemplate, null, List.of());
        ScheduleTemplateResponse response = scheduleTemplateMapper.toResponse(scheduleTemplateRepository.save(scheduleTemplate));
        scheduleAuditService.record(currentUserId, "SCHEDULE_TEMPLATE_CREATED", "SCHEDULE_TEMPLATE", response.id(), null, response);
        return response;
    }

    @Transactional
    public List<ScheduleTemplateResponse> createTemplatesBulk(UUID currentUserId, BulkCreateScheduleTemplatesRequest request) {
        List<ScheduleTemplate> pendingTemplates = new ArrayList<>();

        for (CreateScheduleTemplateRequest item : request.items()) {
            ScheduleTemplate scheduleTemplate = buildTemplate(item);
            scheduleConflictService.assertNoTemplateConflicts(scheduleTemplate, null, pendingTemplates);
            pendingTemplates.add(scheduleTemplate);
        }

        Map<UUID, Integer> slotNumbers = slotNumbers(pendingTemplates.stream()
                .map(ScheduleTemplate::getSlotId)
                .toList());

        List<ScheduleTemplateResponse> responses = scheduleTemplateRepository.saveAll(pendingTemplates).stream()
                .sorted(templateComparator(slotNumbers))
                .map(scheduleTemplateMapper::toResponse)
                .toList();
        responses.forEach(response ->
                scheduleAuditService.record(currentUserId, "SCHEDULE_TEMPLATE_CREATED", "SCHEDULE_TEMPLATE", response.id(), null, response)
        );
        return responses;
    }

    @Transactional
    public ScheduleTemplateImportResponse importTemplates(UUID currentUserId, ImportScheduleTemplatesRequest request) {
        List<ScheduleTemplate> pendingTemplates = new ArrayList<>();
        List<ScheduleTemplateImportErrorResponse> errors = new ArrayList<>();
        List<ScheduleConflictItemResponse> conflicts = new ArrayList<>();

        for (int index = 0; index < request.items().size(); index++) {
            CreateScheduleTemplateRequest item = request.items().get(index);

            try {
                ScheduleTemplate scheduleTemplate = buildTemplate(item);
                scheduleConflictService.assertNoTemplateConflicts(scheduleTemplate, null, pendingTemplates);
                pendingTemplates.add(scheduleTemplate);
            } catch (ScheduleConflictException exception) {
                conflicts.add(toConflictItem(exception));
                errors.add(new ScheduleTemplateImportErrorResponse(index, exception.getErrorCode(), exception.getMessage()));
            } catch (AppException exception) {
                errors.add(new ScheduleTemplateImportErrorResponse(index, exception.getErrorCode(), exception.getMessage()));
            }
        }

        if (!errors.isEmpty() || !conflicts.isEmpty()) {
            return new ScheduleTemplateImportResponse(
                    request.items().size(),
                    0,
                    0,
                    errors.size(),
                    List.copyOf(errors),
                    List.copyOf(conflicts)
            );
        }

        List<ScheduleTemplateResponse> savedTemplates = scheduleTemplateRepository.saveAll(pendingTemplates).stream()
                .map(scheduleTemplateMapper::toResponse)
                .toList();
        savedTemplates.forEach(response ->
                scheduleAuditService.record(currentUserId, "SCHEDULE_TEMPLATE_IMPORTED", "SCHEDULE_TEMPLATE", response.id(), null, response)
        );

        return new ScheduleTemplateImportResponse(
                request.items().size(),
                pendingTemplates.size(),
                0,
                0,
                List.of(),
                List.of()
        );
    }

    @Transactional
    public ScheduleTemplateResponse updateTemplate(UUID currentUserId, UUID templateId, UpdateScheduleTemplateRequest request) {
        ScheduleTemplate scheduleTemplate = scheduleTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ScheduleTemplateNotFoundException(templateId));
        if (scheduleTemplate.getStatus() == ScheduleTemplateStatus.ARCHIVED) {
            throw new ScheduleConflictException(
                    "INVALID_STATE_TRANSITION",
                    "Schedule template state transition is not allowed",
                    Map.of(
                            "templateId", templateId,
                            "fromStatus", scheduleTemplate.getStatus().name(),
                            "targetStatus", request.active() ? ScheduleTemplateStatus.ACTIVE.name() : ScheduleTemplateStatus.DRAFT.name()
                    )
            );
        }
        ScheduleTemplateResponse oldValue = scheduleTemplateMapper.toResponse(scheduleTemplate);

        AcademicSemester semester = requireSemester(request.semesterId());
        LessonSlot lessonSlot = requireUsableSlot(request.slotId());
        UUID roomId = normalizeRoomId(request.lessonFormat(), request.roomId());
        if (roomId != null) {
            requireUsableRoom(roomId);
        }
        validateLessonFormat(request.lessonFormat(), roomId);

        scheduleTemplate.setSemesterId(semester.getId());
        scheduleTemplate.setGroupId(request.groupId());
        scheduleTemplate.setSubjectId(request.subjectId());
        scheduleTemplate.setTeacherId(request.teacherId());
        scheduleTemplate.setDayOfWeek(request.dayOfWeek());
        scheduleTemplate.setSlotId(lessonSlot.getId());
        scheduleTemplate.setWeekType(request.weekType());
        scheduleTemplate.setSubgroup(normalizeSubgroup(request.subgroup()));
        scheduleTemplate.setLessonType(request.lessonType());
        scheduleTemplate.setLessonFormat(request.lessonFormat());
        scheduleTemplate.setRoomId(roomId);
        scheduleTemplate.setOnlineMeetingUrl(normalize(request.onlineMeetingUrl()));
        scheduleTemplate.setNotes(normalize(request.notes()));
        scheduleTemplate.setStatus(request.active() ? ScheduleTemplateStatus.ACTIVE : ScheduleTemplateStatus.DRAFT);
        scheduleTemplate.setActive(request.active());

        scheduleConflictService.assertNoTemplateConflicts(scheduleTemplate, templateId, List.of());
        ScheduleTemplateResponse response = scheduleTemplateMapper.toResponse(scheduleTemplateRepository.save(scheduleTemplate));
        scheduleAuditService.record(currentUserId, "SCHEDULE_TEMPLATE_UPDATED", "SCHEDULE_TEMPLATE", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public void deleteTemplate(UUID currentUserId, UUID templateId) {
        ScheduleTemplate scheduleTemplate = scheduleTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ScheduleTemplateNotFoundException(templateId));
        ScheduleTemplateResponse oldValue = scheduleTemplateMapper.toResponse(scheduleTemplate);
        scheduleTemplate.setStatus(ScheduleTemplateStatus.ARCHIVED);
        scheduleTemplate.setActive(false);
        ScheduleTemplateResponse response = scheduleTemplateMapper.toResponse(scheduleTemplateRepository.save(scheduleTemplate));
        scheduleAuditService.record(currentUserId, "SCHEDULE_TEMPLATE_DELETED", "SCHEDULE_TEMPLATE", response.id(), oldValue, response);
    }

    @Transactional(readOnly = true)
    public List<ScheduleTemplateResponse> getTemplatesBySemester(UUID semesterId) {
        requireSemester(semesterId);
        List<ScheduleTemplate> templates = scheduleTemplateRepository.findAllBySemesterIdAndActiveTrueOrderByCreatedAtAsc(semesterId);
        Map<UUID, Integer> slotNumbers = slotNumbers(templates.stream().map(ScheduleTemplate::getSlotId).toList());

        return templates.stream()
                .sorted(templateComparator(slotNumbers))
                .map(scheduleTemplateMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleTemplateResponse> getTemplatesByGroup(UUID groupId) {
        List<ScheduleTemplate> templates = scheduleTemplateRepository.findAllByGroupIdAndActiveTrueOrderByCreatedAtDesc(groupId);
        Map<UUID, Integer> slotNumbers = slotNumbers(templates.stream().map(ScheduleTemplate::getSlotId).toList());

        return templates.stream()
                .sorted(templateComparator(slotNumbers))
                .map(scheduleTemplateMapper::toResponse)
                .toList();
    }

    @Transactional
    public ScheduleTemplateResponse restoreTemplate(UUID currentUserId, UUID templateId) {
        ScheduleTemplate scheduleTemplate = scheduleTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ScheduleTemplateNotFoundException(templateId));
        if (scheduleTemplate.getStatus() != ScheduleTemplateStatus.ARCHIVED || scheduleTemplate.isActive()) {
            throw new ScheduleConflictException(
                    "INVALID_STATE_TRANSITION",
                    "Schedule template state transition is not allowed",
                    Map.of(
                            "templateId", templateId,
                            "fromStatus", scheduleTemplate.getStatus().name(),
                            "targetStatus", ScheduleTemplateStatus.ACTIVE.name()
                    )
            );
        }
        ScheduleTemplateResponse oldValue = scheduleTemplateMapper.toResponse(scheduleTemplate);
        scheduleTemplate.setStatus(ScheduleTemplateStatus.ACTIVE);
        scheduleTemplate.setActive(true);
        scheduleConflictService.assertNoTemplateConflicts(scheduleTemplate, templateId, List.of());
        ScheduleTemplateResponse response = scheduleTemplateMapper.toResponse(scheduleTemplateRepository.save(scheduleTemplate));
        scheduleAuditService.record(currentUserId, "SCHEDULE_TEMPLATE_RESTORED", "SCHEDULE_TEMPLATE", response.id(), oldValue, response);
        return response;
    }

    private ScheduleTemplate buildTemplate(CreateScheduleTemplateRequest request) {
        AcademicSemester semester = requireSemester(request.semesterId());
        LessonSlot lessonSlot = requireUsableSlot(request.slotId());
        UUID roomId = normalizeRoomId(request.lessonFormat(), request.roomId());
        if (roomId != null) {
            requireUsableRoom(roomId);
        }
        validateLessonFormat(request.lessonFormat(), roomId);

        return scheduleTemplateFactory.newScheduleTemplate(
                semester.getId(),
                request.groupId(),
                request.subjectId(),
                request.teacherId(),
                request.dayOfWeek(),
                lessonSlot.getId(),
                request.weekType(),
                normalizeSubgroup(request.subgroup()),
                request.lessonType(),
                request.lessonFormat(),
                roomId,
                request.onlineMeetingUrl(),
                request.notes(),
                request.active()
        );
    }

    private AcademicSemester requireSemester(UUID semesterId) {
        return academicSemesterRepository.findById(semesterId)
                .orElseThrow(() -> new AcademicSemesterNotFoundException(semesterId));
    }

    private LessonSlot requireUsableSlot(UUID slotId) {
        LessonSlot lessonSlot = lessonSlotRepository.findById(slotId)
                .orElseThrow(() -> new LessonSlotNotFoundException(slotId));
        if (!CanonicalLessonSlots.isCanonicalActiveSlot(lessonSlot)) {
            throw new ScheduleValidationException(
                    "LESSON_SLOT_NOT_CANONICAL",
                    "Lesson slot must be one of the active canonical pairs 1..8",
                    Map.of("slotId", slotId)
            );
        }
        return lessonSlot;
    }

    private Room requireUsableRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));
        if (!room.isActive()) {
            throw new ScheduleValidationException(
                    "ROOM_INACTIVE",
                    "Room must be active",
                    Map.of("roomId", roomId)
            );
        }
        return room;
    }

    private void validateLessonFormat(LessonFormat lessonFormat, UUID roomId) {
        if (lessonFormat == LessonFormat.OFFLINE && roomId == null) {
            throw new ScheduleValidationException(
                    "ROOM_REQUIRED_FOR_OFFLINE_LESSON",
                    "Offline lessons require roomId"
            );
        }
    }

    private UUID normalizeRoomId(LessonFormat lessonFormat, UUID roomId) {
        return lessonFormat == LessonFormat.ONLINE ? null : roomId;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Subgroup normalizeSubgroup(Subgroup subgroup) {
        return subgroup == null ? Subgroup.ALL : subgroup;
    }

    private Map<UUID, Integer> slotNumbers(List<UUID> slotIds) {
        return lessonSlotRepository.findAllById(slotIds).stream()
                .collect(Collectors.toMap(LessonSlot::getId, LessonSlot::getNumber));
    }

    private Comparator<ScheduleTemplate> templateComparator(Map<UUID, Integer> slotNumbers) {
        return Comparator.comparing((ScheduleTemplate template) -> template.getDayOfWeek().getValue())
                .thenComparing(template -> slotNumbers.getOrDefault(template.getSlotId(), Integer.MAX_VALUE))
                .thenComparing(ScheduleTemplate::getCreatedAt);
    }

    private ScheduleConflictItemResponse toConflictItem(ScheduleConflictException exception) {
        Map<String, Object> details = exception.getDetails();
        UUID conflictingEntityId = (UUID) details.get("existingTemplateId");
        String conflictType = (String) details.get("conflictType");

        return new ScheduleConflictItemResponse(
                switch (conflictType) {
                    case "DUPLICATE_LESSON" -> "DUPLICATE_LESSON_CONFLICT";
                    case "TEACHER" -> "TEACHER_CONFLICT";
                    case "ROOM" -> "ROOM_CONFLICT";
                    default -> "GROUP_SUBGROUP_CONFLICT";
                },
                exception.getMessage(),
                conflictingEntityId,
                conflictingEntityId == null ? null : "SCHEDULE_TEMPLATE",
                null,
                dayOfWeek(details.get("dayOfWeek")),
                uuid(details.get("slotId")),
                uuid(details.get("groupId")),
                subgroup(details.get("subgroup")),
                uuid(details.get("teacherId")),
                uuid(details.get("roomId"))
        );
    }

    private DayOfWeek dayOfWeek(Object value) {
        return value == null ? null : DayOfWeek.valueOf(value.toString());
    }

    private UUID uuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return value == null ? null : UUID.fromString(value.toString());
    }

    private Subgroup subgroup(Object value) {
        if (value instanceof Subgroup subgroup) {
            return subgroup;
        }
        return value == null ? null : Subgroup.valueOf(value.toString());
    }
}

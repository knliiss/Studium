package dev.knalis.schedule.service.override;

import dev.knalis.contracts.event.ScheduleExtraLessonCreatedEventV1;
import dev.knalis.contracts.event.ScheduleLessonCancelledEventV1;
import dev.knalis.contracts.event.ScheduleLessonFormatV1;
import dev.knalis.contracts.event.ScheduleLessonReplacedEventV1;
import dev.knalis.contracts.event.ScheduleLessonTypeV1;
import dev.knalis.contracts.event.ScheduleOverrideCreatedEventV1;
import dev.knalis.contracts.event.ScheduleOverrideTypeV1;
import dev.knalis.schedule.dto.request.CreateScheduleOverrideRequest;
import dev.knalis.schedule.dto.request.UpdateScheduleOverrideRequest;
import dev.knalis.schedule.dto.response.ScheduleOverrideResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.LessonSlot;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.Room;
import dev.knalis.schedule.entity.ScheduleOverride;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.Subgroup;
import dev.knalis.schedule.exception.AcademicSemesterNotFoundException;
import dev.knalis.schedule.exception.LessonSlotNotFoundException;
import dev.knalis.schedule.exception.RoomNotFoundException;
import dev.knalis.schedule.exception.ScheduleAccessDeniedException;
import dev.knalis.schedule.exception.ScheduleOverrideNotFoundException;
import dev.knalis.schedule.exception.ScheduleTemplateNotFoundException;
import dev.knalis.schedule.exception.ScheduleValidationException;
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
import dev.knalis.schedule.service.schedule.AcademicWeekSupport;
import dev.knalis.schedule.service.schedule.ScheduleConflictService;
import dev.knalis.schedule.service.slot.CanonicalLessonSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleOverrideService {

    private final ScheduleOverrideRepository scheduleOverrideRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final TeacherDebtRepository teacherDebtRepository;
    private final AcademicSemesterRepository academicSemesterRepository;
    private final LessonSlotRepository lessonSlotRepository;
    private final RoomRepository roomRepository;
    private final TeacherDebtFactory teacherDebtFactory;
    private final ScheduleOverrideFactory scheduleOverrideFactory;
    private final ScheduleOverrideMapper scheduleOverrideMapper;
    private final ScheduleConflictService scheduleConflictService;
    private final ScheduleAuditService scheduleAuditService;
    private final ScheduleEventPublisher scheduleEventPublisher;

    @Transactional
    public ScheduleOverrideResponse createOverride(
            UUID currentUserId,
            boolean admin,
            CreateScheduleOverrideRequest request
    ) {
        OverrideDraft draft = buildDraft(
                currentUserId,
                admin,
                request.semesterId(),
                request.templateId(),
                request.overrideType(),
                request.date(),
                request.groupId(),
                request.subjectId(),
                request.teacherId(),
                request.slotId(),
                request.subgroup(),
                request.lessonType(),
                request.lessonFormat(),
                request.roomId(),
                request.onlineMeetingUrl(),
                request.notes(),
                null
        );

        ScheduleOverride scheduleOverride = scheduleOverrideFactory.newScheduleOverride(
                draft.semester().getId(),
                draft.templateId(),
                draft.overrideType(),
                draft.date(),
                draft.groupId(),
                draft.subjectId(),
                draft.teacherId(),
                draft.slotId(),
                draft.subgroup(),
                draft.lessonType(),
                draft.lessonFormat(),
                draft.roomId(),
                draft.onlineMeetingUrl(),
                draft.notes(),
                currentUserId
        );

        scheduleConflictService.assertNoOverrideConflicts(
                scheduleOverride,
                draft.semester(),
                null,
                draft.ignoredTemplateId()
        );

        ScheduleOverride savedOverride = scheduleOverrideRepository.save(scheduleOverride);
        createTeacherDebt(savedOverride);
        publishOverrideCreatedEvent(savedOverride, draft, currentUserId);
        ScheduleOverrideResponse response = scheduleOverrideMapper.toResponse(savedOverride);
        scheduleAuditService.record(currentUserId, "SCHEDULE_OVERRIDE_CREATED", "SCHEDULE_OVERRIDE", response.id(), null, response);
        return response;
    }

    @Transactional
    public ScheduleOverrideResponse updateOverride(
            UUID currentUserId,
            boolean admin,
            UUID overrideId,
            UpdateScheduleOverrideRequest request
    ) {
        ScheduleOverride scheduleOverride = scheduleOverrideRepository.findById(overrideId)
                .orElseThrow(() -> new ScheduleOverrideNotFoundException(overrideId));
        ScheduleOverrideResponse oldValue = scheduleOverrideMapper.toResponse(scheduleOverride);

        enforceOverrideAccess(scheduleOverride, currentUserId, admin);

        OverrideDraft draft = buildDraft(
                currentUserId,
                admin,
                request.semesterId(),
                request.templateId(),
                request.overrideType(),
                request.date(),
                request.groupId(),
                request.subjectId(),
                request.teacherId(),
                request.slotId(),
                request.subgroup(),
                request.lessonType(),
                request.lessonFormat(),
                request.roomId(),
                request.onlineMeetingUrl(),
                request.notes(),
                overrideId
        );

        scheduleOverride.setSemesterId(draft.semester().getId());
        scheduleOverride.setTemplateId(draft.templateId());
        scheduleOverride.setOverrideType(draft.overrideType());
        scheduleOverride.setDate(draft.date());
        scheduleOverride.setGroupId(draft.groupId());
        scheduleOverride.setSubjectId(draft.subjectId());
        scheduleOverride.setTeacherId(draft.teacherId());
        scheduleOverride.setSlotId(draft.slotId());
        scheduleOverride.setSubgroup(draft.subgroup());
        scheduleOverride.setLessonType(draft.lessonType());
        scheduleOverride.setLessonFormat(draft.lessonFormat());
        scheduleOverride.setRoomId(draft.roomId());
        scheduleOverride.setOnlineMeetingUrl(draft.onlineMeetingUrl());
        scheduleOverride.setNotes(draft.notes());

        scheduleConflictService.assertNoOverrideConflicts(
                scheduleOverride,
                draft.semester(),
                overrideId,
                draft.ignoredTemplateId()
        );

        ScheduleOverrideResponse response = scheduleOverrideMapper.toResponse(scheduleOverrideRepository.save(scheduleOverride));
        scheduleAuditService.record(currentUserId, "SCHEDULE_OVERRIDE_UPDATED", "SCHEDULE_OVERRIDE", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public void deleteOverride(UUID currentUserId, boolean admin, UUID overrideId) {
        ScheduleOverride scheduleOverride = scheduleOverrideRepository.findById(overrideId)
                .orElseThrow(() -> new ScheduleOverrideNotFoundException(overrideId));
        ScheduleOverrideResponse oldValue = scheduleOverrideMapper.toResponse(scheduleOverride);

        enforceOverrideAccess(scheduleOverride, currentUserId, admin);
        scheduleOverrideRepository.delete(scheduleOverride);
        scheduleAuditService.record(currentUserId, "SCHEDULE_OVERRIDE_DELETED", "SCHEDULE_OVERRIDE", oldValue.id(), oldValue, null);
    }

    @Transactional(readOnly = true)
    public List<ScheduleOverrideResponse> getOverridesByDate(LocalDate date, UUID currentUserId, boolean admin) {
        List<ScheduleOverride> overrides = scheduleOverrideRepository.findAllByDateOrderByCreatedAtAsc(date).stream()
                .filter(scheduleOverride -> admin || currentUserId.equals(scheduleOverride.getTeacherId()))
                .toList();

        Map<UUID, Integer> slotNumbers = lessonSlotRepository.findAllById(
                overrides.stream().map(ScheduleOverride::getSlotId).toList()
        ).stream().collect(Collectors.toMap(LessonSlot::getId, LessonSlot::getNumber));

        return overrides.stream()
                .sorted(Comparator
                        .comparing((ScheduleOverride scheduleOverride) -> slotNumbers.getOrDefault(
                                scheduleOverride.getSlotId(),
                                Integer.MAX_VALUE
                        ))
                        .thenComparing(ScheduleOverride::getCreatedAt))
                .map(scheduleOverrideMapper::toResponse)
                .toList();
    }

    private OverrideDraft buildDraft(
            UUID currentUserId,
            boolean admin,
            UUID semesterId,
            UUID templateId,
            OverrideType overrideType,
            LocalDate date,
            UUID groupId,
            UUID subjectId,
            UUID teacherId,
            UUID slotId,
            Subgroup subgroup,
            LessonType lessonType,
            LessonFormat lessonFormat,
            UUID roomId,
            String onlineMeetingUrl,
            String notes,
            UUID overrideId
    ) {
        if (overrideType == OverrideType.EXTRA) {
            if (templateId != null) {
                throw new ScheduleValidationException(
                        "EXTRA_OVERRIDE_TEMPLATE_NOT_ALLOWED",
                        "Extra overrides must not reference templateId"
                );
            }
            if (semesterId == null) {
                throw new ScheduleValidationException(
                        "SEMESTER_ID_REQUIRED",
                        "semesterId is required for extra overrides"
                );
            }
            if (groupId == null || subjectId == null || teacherId == null || slotId == null
                    || lessonType == null || lessonFormat == null) {
                throw new ScheduleValidationException(
                        "INCOMPLETE_EXTRA_OVERRIDE",
                        "Extra overrides require groupId, subjectId, teacherId, slotId, lessonType, and lessonFormat"
                );
            }

            AcademicSemester semester = requireSemester(semesterId);
            validateOverrideDate(semester, date);
            LessonSlot lessonSlot = requireUsableSlot(slotId);
            UUID normalizedRoomId = normalizeRoomId(lessonFormat, roomId);
            if (normalizedRoomId != null) {
                requireUsableRoom(normalizedRoomId);
            }
            validateLessonFormat(lessonFormat, normalizedRoomId);
            enforceTeacherAccess(currentUserId, admin, teacherId);

            return new OverrideDraft(
                    semester,
                    null,
                    null,
                    null,
                    OverrideType.EXTRA,
                    date,
                    groupId,
                    subjectId,
                    teacherId,
                    lessonSlot.getId(),
                    normalizeSubgroup(subgroup),
                    lessonType,
                    lessonFormat,
                    normalizedRoomId,
                    normalize(onlineMeetingUrl),
                    normalize(notes)
            );
        }

        if (templateId == null) {
            throw new ScheduleValidationException(
                    "TEMPLATE_ID_REQUIRED",
                    "templateId is required for cancel and replace overrides"
            );
        }

        ScheduleTemplate scheduleTemplate = requireTemplate(templateId);
        AcademicSemester semester = requireSemester(scheduleTemplate.getSemesterId());
        if (semesterId != null && !semesterId.equals(semester.getId())) {
            throw new ScheduleValidationException(
                    "SEMESTER_ID_MISMATCH",
                    "semesterId must match the template semester",
                    Map.of(
                            "semesterId", semesterId,
                            "templateSemesterId", semester.getId()
                    )
            );
        }
        scheduleConflictService.assertTemplateOccurrenceExists(scheduleTemplate, semester, date);
        scheduleConflictService.assertNoDuplicateTemplateOverride(templateId, date, overrideId);
        enforceTeacherAccess(currentUserId, admin, scheduleTemplate.getTeacherId());

        if (overrideType == OverrideType.CANCEL) {
            return new OverrideDraft(
                    semester,
                    templateId,
                    null,
                    scheduleTemplate,
                    OverrideType.CANCEL,
                    date,
                    scheduleTemplate.getGroupId(),
                    scheduleTemplate.getSubjectId(),
                    scheduleTemplate.getTeacherId(),
                    scheduleTemplate.getSlotId(),
                    scheduleTemplate.getSubgroup(),
                    scheduleTemplate.getLessonType(),
                    scheduleTemplate.getLessonFormat(),
                    scheduleTemplate.getRoomId(),
                    scheduleTemplate.getOnlineMeetingUrl(),
                    normalize(notes)
            );
        }

        if (overrideId == null && lessonType == null) {
            throw new ScheduleValidationException(
                    "LESSON_TYPE_REQUIRED",
                    "lessonType is required for replace overrides"
            );
        }

        UUID resolvedTeacherId = teacherId != null ? teacherId : scheduleTemplate.getTeacherId();
        LessonType resolvedLessonType = lessonType != null ? lessonType : scheduleTemplate.getLessonType();
        LessonFormat resolvedLessonFormat = lessonFormat != null ? lessonFormat : scheduleTemplate.getLessonFormat();
        UUID resolvedSlotId = slotId != null ? slotId : scheduleTemplate.getSlotId();
        UUID requestedRoomId = roomId != null ? roomId : scheduleTemplate.getRoomId();
        UUID normalizedRoomId = normalizeRoomId(resolvedLessonFormat, requestedRoomId);
        LessonSlot resolvedSlot = requireUsableSlot(resolvedSlotId);
        if (normalizedRoomId != null) {
            requireUsableRoom(normalizedRoomId);
        }
        validateLessonFormat(resolvedLessonFormat, normalizedRoomId);
        enforceTeacherAccess(currentUserId, admin, resolvedTeacherId);

        return new OverrideDraft(
                semester,
                templateId,
                templateId,
                scheduleTemplate,
                OverrideType.REPLACE,
                date,
                groupId != null ? groupId : scheduleTemplate.getGroupId(),
                subjectId != null ? subjectId : scheduleTemplate.getSubjectId(),
                resolvedTeacherId,
                resolvedSlot.getId(),
                subgroup != null ? subgroup : scheduleTemplate.getSubgroup(),
                resolvedLessonType,
                resolvedLessonFormat,
                normalizedRoomId,
                normalize(onlineMeetingUrl) != null ? normalize(onlineMeetingUrl) : scheduleTemplate.getOnlineMeetingUrl(),
                normalize(notes) != null ? normalize(notes) : scheduleTemplate.getNotes()
        );
    }

    private void enforceOverrideAccess(ScheduleOverride scheduleOverride, UUID currentUserId, boolean admin) {
        if (admin || currentUserId.equals(scheduleOverride.getTeacherId())) {
            return;
        }

        throw new ScheduleAccessDeniedException(
                "SCHEDULE_OVERRIDE_ACCESS_DENIED",
                "Teachers can modify only their own overrides",
                Map.of(
                        "overrideId", scheduleOverride.getId(),
                        "currentUserId", currentUserId
                )
        );
    }

    private void enforceTeacherAccess(UUID currentUserId, boolean admin, UUID teacherId) {
        if (admin || currentUserId.equals(teacherId)) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("teacherId", teacherId);
        details.put("currentUserId", currentUserId);

        throw new ScheduleAccessDeniedException(
                "SCHEDULE_OVERRIDE_ACCESS_DENIED",
                "Teachers can create or modify overrides only for their own lessons",
                details
        );
    }

    private void validateOverrideDate(AcademicSemester semester, LocalDate date) {
        if (!AcademicWeekSupport.isWithinSemester(semester, date)) {
            throw new ScheduleValidationException(
                    "OVERRIDE_DATE_OUTSIDE_SEMESTER",
                    "Override date must be within the semester date range",
                    Map.of(
                            "semesterId", semester.getId(),
                            "date", date.toString()
                    )
            );
        }
        if (!AcademicWeekSupport.isOnOrAfterWeekOne(semester, date)) {
            throw new ScheduleValidationException(
                    "OVERRIDE_DATE_BEFORE_WEEK_ONE",
                    "Override date must be on or after the academic week one start date",
                    Map.of(
                            "semesterId", semester.getId(),
                            "date", date.toString(),
                            "weekOneStartDate", semester.getWeekOneStartDate().toString()
                    )
            );
        }
    }

    private AcademicSemester requireSemester(UUID semesterId) {
        return academicSemesterRepository.findById(semesterId)
                .orElseThrow(() -> new AcademicSemesterNotFoundException(semesterId));
    }

    private ScheduleTemplate requireTemplate(UUID templateId) {
        return scheduleTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ScheduleTemplateNotFoundException(templateId));
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

    private void createTeacherDebt(ScheduleOverride scheduleOverride) {
        if (scheduleOverride.getOverrideType() != OverrideType.CANCEL) {
            return;
        }

        teacherDebtRepository.save(teacherDebtFactory.newOpenDebt(scheduleOverride));
    }

    private void publishOverrideCreatedEvent(ScheduleOverride scheduleOverride, OverrideDraft draft, UUID currentUserId) {
        if (scheduleOverride.getOverrideType() == OverrideType.CANCEL) {
            scheduleEventPublisher.publishScheduleLessonCancelled(new ScheduleLessonCancelledEventV1(
                    UUID.randomUUID(),
                    Instant.now(),
                    scheduleOverride.getSemesterId(),
                    scheduleOverride.getId(),
                    scheduleOverride.getDate(),
                    scheduleOverride.getGroupId(),
                    scheduleOverride.getSubjectId(),
                    scheduleOverride.getTeacherId(),
                    scheduleOverride.getSlotId(),
                    toContractLessonType(scheduleOverride.getLessonType()),
                    toContractLessonFormat(scheduleOverride.getLessonFormat()),
                    scheduleOverride.getRoomId(),
                    scheduleOverride.getOnlineMeetingUrl(),
                    currentUserId,
                    scheduleOverride.getNotes()
            ));
            return;
        }

        if (scheduleOverride.getOverrideType() == OverrideType.EXTRA) {
            scheduleEventPublisher.publishScheduleExtraLessonCreated(new ScheduleExtraLessonCreatedEventV1(
                    UUID.randomUUID(),
                    Instant.now(),
                    scheduleOverride.getSemesterId(),
                    scheduleOverride.getId(),
                    scheduleOverride.getDate(),
                    scheduleOverride.getGroupId(),
                    scheduleOverride.getSubjectId(),
                    scheduleOverride.getTeacherId(),
                    scheduleOverride.getSlotId(),
                    toContractLessonType(scheduleOverride.getLessonType()),
                    toContractLessonFormat(scheduleOverride.getLessonFormat()),
                    scheduleOverride.getRoomId(),
                    scheduleOverride.getOnlineMeetingUrl(),
                    scheduleOverride.getNotes(),
                    currentUserId
            ));
            return;
        }

        ScheduleTemplate template = draft.template();
        if (template != null && hasCriticalReplacementChange(template, scheduleOverride)) {
            scheduleEventPublisher.publishScheduleLessonReplaced(new ScheduleLessonReplacedEventV1(
                    UUID.randomUUID(),
                    Instant.now(),
                    scheduleOverride.getSemesterId(),
                    scheduleOverride.getId(),
                    scheduleOverride.getDate(),
                    template.getTeacherId(),
                    scheduleOverride.getTeacherId(),
                    template.getRoomId(),
                    scheduleOverride.getRoomId(),
                    template.getSlotId(),
                    scheduleOverride.getSlotId(),
                    toContractLessonType(scheduleOverride.getLessonType()),
                    toContractLessonFormat(template.getLessonFormat()),
                    toContractLessonFormat(scheduleOverride.getLessonFormat()),
                    scheduleOverride.getOnlineMeetingUrl(),
                    scheduleOverride.getGroupId(),
                    scheduleOverride.getSubjectId(),
                    currentUserId
            ));
            return;
        }

        scheduleEventPublisher.publishScheduleOverrideCreated(new ScheduleOverrideCreatedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                scheduleOverride.getSemesterId(),
                scheduleOverride.getTemplateId(),
                scheduleOverride.getId(),
                toContractOverrideType(scheduleOverride.getOverrideType()),
                scheduleOverride.getDate(),
                scheduleOverride.getGroupId(),
                scheduleOverride.getSubjectId(),
                scheduleOverride.getTeacherId(),
                scheduleOverride.getSlotId(),
                toContractLessonType(scheduleOverride.getLessonType()),
                toContractLessonFormat(scheduleOverride.getLessonFormat()),
                scheduleOverride.getRoomId(),
                scheduleOverride.getOnlineMeetingUrl(),
                scheduleOverride.getNotes(),
                currentUserId
        ));
    }

    private boolean hasCriticalReplacementChange(ScheduleTemplate template, ScheduleOverride scheduleOverride) {
        return !template.getTeacherId().equals(scheduleOverride.getTeacherId())
                || !Objects.equals(template.getRoomId(), scheduleOverride.getRoomId())
                || !template.getSlotId().equals(scheduleOverride.getSlotId())
                || template.getLessonFormat() != scheduleOverride.getLessonFormat();
    }

    private ScheduleLessonFormatV1 toContractLessonFormat(LessonFormat lessonFormat) {
        return lessonFormat == LessonFormat.ONLINE
                ? ScheduleLessonFormatV1.ONLINE
                : ScheduleLessonFormatV1.OFFLINE;
    }

    private ScheduleLessonTypeV1 toContractLessonType(LessonType lessonType) {
        if (lessonType == null) {
            return null;
        }

        return switch (lessonType) {
            case LECTURE -> ScheduleLessonTypeV1.LECTURE;
            case PRACTICAL -> ScheduleLessonTypeV1.PRACTICAL;
            case LABORATORY -> ScheduleLessonTypeV1.LABORATORY;
        };
    }

    private ScheduleOverrideTypeV1 toContractOverrideType(OverrideType overrideType) {
        return switch (overrideType) {
            case CANCEL -> ScheduleOverrideTypeV1.CANCEL;
            case REPLACE -> ScheduleOverrideTypeV1.REPLACE;
            case EXTRA -> ScheduleOverrideTypeV1.EXTRA;
        };
    }

    private record OverrideDraft(
            AcademicSemester semester,
            UUID templateId,
            UUID ignoredTemplateId,
            ScheduleTemplate template,
            OverrideType overrideType,
            LocalDate date,
            UUID groupId,
            UUID subjectId,
            UUID teacherId,
            UUID slotId,
            Subgroup subgroup,
            LessonType lessonType,
            LessonFormat lessonFormat,
            UUID roomId,
            String onlineMeetingUrl,
            String notes
    ) {
    }
}

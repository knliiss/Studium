package dev.knalis.education.service.curriculum;

import dev.knalis.education.dto.request.CreateGroupCurriculumOverrideRequest;
import dev.knalis.education.dto.request.UpdateGroupCurriculumOverrideRequest;
import dev.knalis.education.dto.response.GroupCurriculumOverrideResponse;
import dev.knalis.education.entity.GroupCurriculumOverride;
import dev.knalis.education.exception.GroupCurriculumOverrideAlreadyExistsException;
import dev.knalis.education.exception.GroupCurriculumOverrideNotFoundException;
import dev.knalis.education.exception.GroupNotFoundException;
import dev.knalis.education.exception.SubjectNotFoundException;
import dev.knalis.education.repository.GroupCurriculumOverrideRepository;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupCurriculumOverrideService {

    private final GroupCurriculumOverrideRepository overrideRepository;
    private final GroupRepository groupRepository;
    private final SubjectRepository subjectRepository;

    @Transactional(readOnly = true)
    public List<GroupCurriculumOverrideResponse> listGroupOverrides(UUID groupId) {
        ensureGroupExists(groupId);
        return overrideRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public GroupCurriculumOverrideResponse createGroupOverride(UUID groupId, CreateGroupCurriculumOverrideRequest request) {
        ensureGroupExists(groupId);
        ensureSubjectExists(request.subjectId());
        if (overrideRepository.existsByGroupIdAndSubjectId(groupId, request.subjectId())) {
            throw new GroupCurriculumOverrideAlreadyExistsException(groupId, request.subjectId());
        }
        GroupCurriculumOverride override = new GroupCurriculumOverride();
        override.setGroupId(groupId);
        override.setSubjectId(request.subjectId());
        applyUpdate(override, request.enabled(), request.lectureCountOverride(), request.practiceCountOverride(),
                request.labCountOverride(), request.notes());
        return toResponse(overrideRepository.save(override));
    }

    @Transactional
    public GroupCurriculumOverrideResponse updateGroupOverride(
            UUID groupId,
            UUID overrideId,
            UpdateGroupCurriculumOverrideRequest request
    ) {
        ensureGroupExists(groupId);
        GroupCurriculumOverride override = overrideRepository.findByIdAndGroupId(overrideId, groupId)
                .orElseThrow(() -> new GroupCurriculumOverrideNotFoundException(overrideId));
        applyUpdate(override, request.enabled(), request.lectureCountOverride(), request.practiceCountOverride(),
                request.labCountOverride(), request.notes());
        return toResponse(overrideRepository.save(override));
    }

    @Transactional
    public void deleteGroupOverride(UUID groupId, UUID overrideId) {
        ensureGroupExists(groupId);
        GroupCurriculumOverride override = overrideRepository.findByIdAndGroupId(overrideId, groupId)
                .orElseThrow(() -> new GroupCurriculumOverrideNotFoundException(overrideId));
        overrideRepository.delete(override);
    }

    private void ensureGroupExists(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
    }

    private void ensureSubjectExists(UUID subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new SubjectNotFoundException(subjectId);
        }
    }

    private void applyUpdate(
            GroupCurriculumOverride override,
            boolean enabled,
            Integer lectureCountOverride,
            Integer practiceCountOverride,
            Integer labCountOverride,
            String notes
    ) {
        override.setEnabled(enabled);
        override.setLectureCountOverride(lectureCountOverride);
        override.setPracticeCountOverride(practiceCountOverride);
        override.setLabCountOverride(labCountOverride);
        override.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
    }

    private GroupCurriculumOverrideResponse toResponse(GroupCurriculumOverride override) {
        return new GroupCurriculumOverrideResponse(
                override.getId(),
                override.getGroupId(),
                override.getSubjectId(),
                override.isEnabled(),
                override.getLectureCountOverride(),
                override.getPracticeCountOverride(),
                override.getLabCountOverride(),
                override.getNotes(),
                override.getCreatedAt(),
                override.getUpdatedAt()
        );
    }
}

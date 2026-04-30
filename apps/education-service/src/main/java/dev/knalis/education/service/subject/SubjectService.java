package dev.knalis.education.service.subject;

import dev.knalis.education.dto.request.CreateSubjectRequest;
import dev.knalis.education.dto.request.UpdateSubjectRequest;
import dev.knalis.education.dto.response.SubjectPageResponse;
import dev.knalis.education.dto.response.SubjectResponse;
import dev.knalis.education.entity.Subject;
import dev.knalis.education.entity.SubjectGroup;
import dev.knalis.education.entity.SubjectTeacher;
import dev.knalis.education.exception.GroupNotFoundException;
import dev.knalis.education.exception.SubjectNotFoundException;
import dev.knalis.education.factory.subject.SubjectFactory;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectGroupRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import dev.knalis.education.service.common.EducationAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "name"
    );
    
    private final SubjectRepository subjectRepository;
    private final GroupRepository groupRepository;
    private final SubjectGroupRepository subjectGroupRepository;
    private final SubjectTeacherRepository subjectTeacherRepository;
    private final SubjectFactory subjectFactory;
    private final EducationAuditService educationAuditService;
    
    @Transactional
    public SubjectResponse createSubject(UUID currentUserId, CreateSubjectRequest request) {
        List<UUID> groupIds = normalizeGroupIds(request.groupId(), request.groupIds());
        List<UUID> teacherIds = normalizeIds(request.teacherIds());
        Subject subject = subjectFactory.newSubject(primaryGroupId(groupIds), request.name(), request.description());
        Subject savedSubject = subjectRepository.save(subject);
        replaceSubjectGroups(savedSubject.getId(), groupIds);
        replaceSubjectTeachers(savedSubject.getId(), teacherIds);
        SubjectResponse response = toResponse(savedSubject);
        educationAuditService.record(currentUserId, "SUBJECT_CREATED", "SUBJECT", response.id(), null, response);
        return response;
    }

    @Transactional
    public SubjectResponse updateSubject(UUID currentUserId, UUID subjectId, UpdateSubjectRequest request) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
        SubjectResponse oldValue = toResponse(subject);
        List<UUID> groupIds = normalizeGroupIds(request.groupId(), request.groupIds());
        List<UUID> teacherIds = normalizeIds(request.teacherIds());

        subjectFactory.updateSubject(subject, primaryGroupId(groupIds), request.name(), request.description());
        Subject savedSubject = subjectRepository.save(subject);
        replaceSubjectGroups(subjectId, groupIds);
        replaceSubjectTeachers(subjectId, teacherIds);

        SubjectResponse response = toResponse(savedSubject);
        educationAuditService.record(currentUserId, "SUBJECT_UPDATED", "SUBJECT", response.id(), oldValue, response);
        return response;
    }
    
    @Transactional(readOnly = true)
    public SubjectResponse getSubject(UUID subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
        return toResponse(subject);
    }
    
    @Transactional(readOnly = true)
    public SubjectPageResponse getSubjectsByGroup(UUID groupId, int page, int size, String sortBy, String direction) {
        ensureGroupExists(groupId);
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        Page<Subject> subjectPage = subjectRepository.findAllByBoundGroupId(groupId, pageRequest);
        
        return new SubjectPageResponse(
                subjectPage.getContent().stream().map(this::toResponse).toList(),
                subjectPage.getNumber(),
                subjectPage.getSize(),
                subjectPage.getTotalElements(),
                subjectPage.getTotalPages(),
                subjectPage.isFirst(),
                subjectPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> getSubjectsByGroup(UUID groupId) {
        ensureGroupExists(groupId);
        return subjectRepository.findAllByBoundGroupId(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubjectPageResponse listSubjects(int page, int size, String sortBy, String direction, String q) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        String normalizedQuery = q == null ? "" : q.trim();
        Page<Subject> subjectPage = subjectRepository.findAllByNameContainingIgnoreCaseOrderByNameAsc(normalizedQuery, pageRequest);

        return new SubjectPageResponse(
                subjectPage.getContent().stream().map(this::toResponse).toList(),
                subjectPage.getNumber(),
                subjectPage.getSize(),
                subjectPage.getTotalElements(),
                subjectPage.getTotalPages(),
                subjectPage.isFirst(),
                subjectPage.isLast()
        );
    }

    private SubjectResponse toResponse(Subject subject) {
        List<UUID> groupIds = subjectGroupRepository.findAllBySubjectIdOrderByCreatedAtAsc(subject.getId()).stream()
                .map(subjectGroup -> subjectGroup.getGroupId())
                .toList();
        if (groupIds.isEmpty()) {
            groupIds = List.of(subject.getGroupId());
        }
        List<UUID> teacherIds = subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subject.getId()).stream()
                .map(subjectTeacher -> subjectTeacher.getTeacherId())
                .toList();
        UUID primaryGroupId = subject.getGroupId();
        if (primaryGroupId == null) {
            primaryGroupId = groupIds.isEmpty() ? null : groupIds.getFirst();
        } else if (!groupIds.isEmpty() && !groupIds.contains(primaryGroupId)) {
            primaryGroupId = groupIds.getFirst();
        }
        return new SubjectResponse(
                subject.getId(),
                subject.getName(),
                primaryGroupId,
                groupIds,
                teacherIds,
                subject.getDescription(),
                subject.getCreatedAt(),
                subject.getUpdatedAt()
        );
    }

    private List<UUID> normalizeGroupIds(UUID groupId, List<UUID> groupIds) {
        LinkedHashSet<UUID> normalizedIds = new LinkedHashSet<>();
        if (groupId != null) {
            normalizedIds.add(groupId);
        }
        if (groupIds != null) {
            normalizedIds.addAll(groupIds.stream().filter(id -> id != null).toList());
        }
        normalizedIds.forEach(this::ensureGroupExists);
        return List.copyOf(normalizedIds);
    }

    private UUID primaryGroupId(List<UUID> groupIds) {
        return groupIds.isEmpty() ? null : groupIds.getFirst();
    }

    private List<UUID> normalizeIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> normalizedIds = new LinkedHashSet<>(ids.stream().filter(id -> id != null).toList());
        return List.copyOf(normalizedIds);
    }

    private void replaceSubjectGroups(UUID subjectId, List<UUID> groupIds) {
        subjectGroupRepository.deleteAllBySubjectId(subjectId);
        List<SubjectGroup> subjectGroups = new ArrayList<>();
        for (UUID groupId : groupIds) {
            SubjectGroup subjectGroup = new SubjectGroup();
            subjectGroup.setSubjectId(subjectId);
            subjectGroup.setGroupId(groupId);
            subjectGroups.add(subjectGroup);
        }
        subjectGroupRepository.saveAll(subjectGroups);
    }

    private void replaceSubjectTeachers(UUID subjectId, List<UUID> teacherIds) {
        subjectTeacherRepository.deleteAllBySubjectId(subjectId);
        if (teacherIds.isEmpty()) {
            return;
        }
        List<SubjectTeacher> subjectTeachers = new ArrayList<>();
        for (UUID teacherId : teacherIds) {
            SubjectTeacher subjectTeacher = new SubjectTeacher();
            subjectTeacher.setSubjectId(subjectId);
            subjectTeacher.setTeacherId(teacherId);
            subjectTeachers.add(subjectTeacher);
        }
        subjectTeacherRepository.saveAll(subjectTeachers);
    }

    private void ensureGroupExists(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        return ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
    }

    private Sort.Direction resolveSortDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }
}

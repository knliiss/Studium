package dev.knalis.education.service.subject;

import dev.knalis.education.dto.request.CreateSubjectRequest;
import dev.knalis.education.dto.request.UpdateSubjectGroupsRequest;
import dev.knalis.education.dto.request.UpdateSubjectRequest;
import dev.knalis.education.dto.request.UpdateSubjectTeachersRequest;
import dev.knalis.education.dto.response.SubjectPageResponse;
import dev.knalis.education.dto.response.SubjectResponse;
import dev.knalis.education.entity.GroupStudent;
import dev.knalis.education.entity.Subject;
import dev.knalis.education.entity.SubjectGroup;
import dev.knalis.education.entity.SubjectTeacher;
import dev.knalis.education.exception.EducationAccessDeniedException;
import dev.knalis.education.exception.GroupNotFoundException;
import dev.knalis.education.exception.InvalidSubjectBindingException;
import dev.knalis.education.exception.SubjectGroupAlreadyAssignedException;
import dev.knalis.education.exception.SubjectNameAlreadyExistsException;
import dev.knalis.education.exception.SubjectNotFoundException;
import dev.knalis.education.exception.SubjectTeacherAlreadyAssignedException;
import dev.knalis.education.exception.SubjectUpdateConflictException;
import dev.knalis.education.factory.subject.SubjectFactory;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectGroupRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import dev.knalis.education.service.common.EducationAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final GroupStudentRepository groupStudentRepository;
    private final SubjectGroupRepository subjectGroupRepository;
    private final SubjectTeacherRepository subjectTeacherRepository;
    private final SubjectFactory subjectFactory;
    private final EducationAuditService educationAuditService;
    
    @Transactional
    public SubjectResponse createSubject(UUID currentUserId, CreateSubjectRequest request) {
        ensureUniqueNameForCreate(request.name());
        Subject subject = subjectFactory.newSubject(request.name(), request.description());
        Subject savedSubject = subjectRepository.save(subject);
        SubjectResponse response = toResponse(savedSubject);
        educationAuditService.record(currentUserId, "SUBJECT_CREATED", "SUBJECT", response.id(), null, response);
        return response;
    }

    @Transactional
    public SubjectResponse updateSubject(UUID currentUserId, UUID subjectId, UpdateSubjectRequest request) {
        Subject subject = subjectRepository.findWithLockingById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
        SubjectResponse oldValue = toResponse(subject);
        ensureUniqueNameForUpdate(subjectId, request.name());
        subjectFactory.updateSubject(subject, request.name(), request.description());
        Subject savedSubject = subjectRepository.save(subject);

        SubjectResponse response = toResponse(savedSubject);
        educationAuditService.record(currentUserId, "SUBJECT_UPDATED", "SUBJECT", response.id(), oldValue, response);
        return response;
    }
    
    @Transactional(readOnly = true)
    public SubjectResponse getSubject(UUID currentUserId, Set<String> currentRoles, UUID subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
        ensureCanReadSubject(currentUserId, currentRoles, subjectId);
        return toResponse(subject);
    }

    @Transactional(readOnly = true)
    public SubjectResponse getSubject(UUID subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
        return toResponse(subject);
    }
    
    @Transactional(readOnly = true)
    public SubjectPageResponse getSubjectsByGroup(
            UUID currentUserId,
            Set<String> currentRoles,
            UUID groupId,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        ensureGroupExists(groupId);
        ensureCanReadGroupSubjects(currentUserId, currentRoles, groupId);
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
    public SubjectPageResponse listSubjects(
            UUID currentUserId,
            Set<String> currentRoles,
            int page,
            int size,
            String sortBy,
            String direction,
            String q
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        String normalizedQuery = q == null ? "" : q.trim();
        Page<Subject> subjectPage = resolveScopedSubjectPage(currentUserId, currentRoles, normalizedQuery, pageRequest);

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
    public List<UUID> getSubjectGroups(UUID currentUserId, Set<String> currentRoles, UUID subjectId) {
        ensureCanReadSubject(currentUserId, currentRoles, subjectId);
        return resolveGroupIds(requireSubject(subjectId));
    }

    @Transactional
    public SubjectResponse updateSubjectGroups(
            UUID currentUserId,
            UUID subjectId,
            UpdateSubjectGroupsRequest request
    ) {
        Subject subject = requireSubject(subjectId);
        SubjectResponse oldValue = toResponse(subject);
        List<UUID> normalizedGroupIds = normalizeBindingIds("GROUP", subjectId, request.groupIds());
        normalizedGroupIds.forEach(this::ensureGroupExists);

        Map<UUID, SubjectGroup> existingByGroupId = subjectGroupRepository.findAllBySubjectId(subjectId).stream()
                .collect(LinkedHashMap::new, (map, relation) -> map.put(relation.getGroupId(), relation), Map::putAll);

        for (Map.Entry<UUID, SubjectGroup> entry : existingByGroupId.entrySet()) {
            if (!normalizedGroupIds.contains(entry.getKey())) {
                subjectGroupRepository.delete(entry.getValue());
            }
        }

        for (UUID groupId : normalizedGroupIds) {
            if (existingByGroupId.containsKey(groupId)) {
                continue;
            }
            try {
                SubjectGroup subjectGroup = new SubjectGroup();
                subjectGroup.setSubjectId(subjectId);
                subjectGroup.setGroupId(groupId);
                subjectGroupRepository.save(subjectGroup);
            } catch (DataIntegrityViolationException exception) {
                throw new SubjectGroupAlreadyAssignedException(subjectId, groupId);
            }
        }

        subject.setGroupId(normalizedGroupIds.isEmpty() ? null : normalizedGroupIds.getFirst());
        SubjectResponse response;
        try {
            response = toResponse(subjectRepository.save(subject));
        } catch (DataIntegrityViolationException exception) {
            throw new SubjectUpdateConflictException(subjectId, "GROUP_BINDING_CONSTRAINT");
        }
        educationAuditService.record(currentUserId, "SUBJECT_GROUPS_UPDATED", "SUBJECT", response.id(), oldValue, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<UUID> getSubjectTeachers(UUID currentUserId, Set<String> currentRoles, UUID subjectId) {
        ensureCanReadSubject(currentUserId, currentRoles, subjectId);
        return subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId).stream()
                .map(SubjectTeacher::getTeacherId)
                .toList();
    }

    @Transactional
    public SubjectResponse updateSubjectTeachers(
            UUID currentUserId,
            UUID subjectId,
            UpdateSubjectTeachersRequest request
    ) {
        Subject subject = requireSubject(subjectId);
        SubjectResponse oldValue = toResponse(subject);
        List<UUID> normalizedTeacherIds = normalizeBindingIds("TEACHER", subjectId, request.teacherIds());

        Map<UUID, SubjectTeacher> existingByTeacherId = subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)
                .stream()
                .collect(LinkedHashMap::new, (map, relation) -> map.put(relation.getTeacherId(), relation), Map::putAll);

        for (Map.Entry<UUID, SubjectTeacher> entry : existingByTeacherId.entrySet()) {
            if (!normalizedTeacherIds.contains(entry.getKey())) {
                subjectTeacherRepository.delete(entry.getValue());
            }
        }

        for (UUID teacherId : normalizedTeacherIds) {
            if (existingByTeacherId.containsKey(teacherId)) {
                continue;
            }
            try {
                SubjectTeacher subjectTeacher = new SubjectTeacher();
                subjectTeacher.setSubjectId(subjectId);
                subjectTeacher.setTeacherId(teacherId);
                subjectTeacherRepository.save(subjectTeacher);
            } catch (DataIntegrityViolationException exception) {
                throw new SubjectTeacherAlreadyAssignedException(subjectId, teacherId);
            }
        }

        SubjectResponse response = toResponse(subjectRepository.save(subject));
        educationAuditService.record(currentUserId, "SUBJECT_TEACHERS_UPDATED", "SUBJECT", response.id(), oldValue, response);
        return response;
    }

    private SubjectResponse toResponse(Subject subject) {
        List<UUID> groupIds = resolveGroupIds(subject);
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

    private List<UUID> resolveGroupIds(Subject subject) {
        LinkedHashSet<UUID> resolvedIds = new LinkedHashSet<>();
        if (subject.getGroupId() != null) {
            resolvedIds.add(subject.getGroupId());
        }
        resolvedIds.addAll(
                subjectGroupRepository.findAllBySubjectIdOrderByCreatedAtAsc(subject.getId()).stream()
                        .map(SubjectGroup::getGroupId)
                        .toList()
        );
        return List.copyOf(resolvedIds);
    }

    private List<UUID> normalizeBindingIds(String bindingType, UUID subjectId, List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        if (ids.stream().anyMatch(id -> id == null)) {
            throw new InvalidSubjectBindingException(bindingType, subjectId, null);
        }
        LinkedHashSet<UUID> normalizedIds = new LinkedHashSet<>(ids);
        return List.copyOf(normalizedIds);
    }

    private void ensureUniqueNameForCreate(String name) {
        if (subjectRepository.existsByNameIgnoreCase(name.trim())) {
            throw new SubjectNameAlreadyExistsException(name.trim());
        }
    }

    private void ensureUniqueNameForUpdate(UUID subjectId, String name) {
        if (subjectRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), subjectId)) {
            throw new SubjectNameAlreadyExistsException(name.trim());
        }
    }

    private Subject requireSubject(UUID subjectId) {
        return subjectRepository.findWithLockingById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
    }

    private Page<Subject> resolveScopedSubjectPage(
            UUID currentUserId,
            Set<String> currentRoles,
            String query,
            PageRequest pageRequest
    ) {
        if (isAdmin(currentRoles)) {
            return subjectRepository.findAllByNameContainingIgnoreCase(query, pageRequest);
        }
        if (isTeacher(currentRoles)) {
            return subjectRepository.findAllByTeacherIdAndNameContainingIgnoreCase(currentUserId, query, pageRequest);
        }
        List<UUID> groupIds = currentGroupIds(currentUserId);
        if (groupIds.isEmpty()) {
            return Page.empty(pageRequest);
        }
        return subjectRepository.findAllByBoundGroupIdsAndNameContainingIgnoreCase(groupIds, query, pageRequest);
    }

    private void ensureCanReadSubject(UUID currentUserId, Set<String> currentRoles, UUID subjectId) {
        if (isAdmin(currentRoles)) {
            return;
        }
        if (isTeacher(currentRoles)) {
            if (subjectRepository.existsByIdAndTeacherId(subjectId, currentUserId)) {
                return;
            }
            throw new EducationAccessDeniedException();
        }
        List<UUID> groupIds = currentGroupIds(currentUserId);
        if (!groupIds.isEmpty() && subjectRepository.existsByIdAndBoundGroupIds(subjectId, groupIds)) {
            return;
        }
        throw new EducationAccessDeniedException();
    }

    private void ensureCanReadGroupSubjects(UUID currentUserId, Set<String> currentRoles, UUID groupId) {
        if (isAdmin(currentRoles)) {
            return;
        }
        if (isTeacher(currentRoles)) {
            if (subjectRepository.existsByTeacherIdAndBoundGroupId(currentUserId, groupId)) {
                return;
            }
            throw new EducationAccessDeniedException();
        }
        if (groupStudentRepository.existsByUserIdAndGroupId(currentUserId, groupId)) {
            return;
        }
        throw new EducationAccessDeniedException();
    }

    private List<UUID> currentGroupIds(UUID userId) {
        return groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(GroupStudent::getGroupId)
                .distinct()
                .toList();
    }

    private boolean isAdmin(Collection<String> roles) {
        return roles.contains("ROLE_OWNER") || roles.contains("ROLE_ADMIN");
    }

    private boolean isTeacher(Collection<String> roles) {
        return roles.contains("ROLE_TEACHER");
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

package dev.knalis.education.service.group;

import dev.knalis.education.dto.request.CreateGroupStudentRequest;
import dev.knalis.education.dto.request.CreateGroupRequest;
import dev.knalis.education.dto.request.UpdateGroupStudentRequest;
import dev.knalis.education.dto.response.GroupPageResponse;
import dev.knalis.education.dto.response.GroupMembershipResponse;
import dev.knalis.education.dto.response.GroupStudentMembershipResponse;
import dev.knalis.education.dto.response.GroupStudentUserResponse;
import dev.knalis.education.dto.response.GroupResponse;
import dev.knalis.education.entity.Group;
import dev.knalis.education.entity.GroupStudent;
import dev.knalis.education.exception.GroupNotFoundException;
import dev.knalis.education.exception.GroupStudentAlreadyExistsException;
import dev.knalis.education.exception.GroupStudentNotFoundException;
import dev.knalis.education.factory.group.GroupFactory;
import dev.knalis.education.factory.groupstudent.GroupStudentFactory;
import dev.knalis.education.mapper.GroupMapper;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.service.common.EducationAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "name"
    );
    
    private final GroupRepository groupRepository;
    private final GroupStudentRepository groupStudentRepository;
    private final GroupFactory groupFactory;
    private final GroupStudentFactory groupStudentFactory;
    private final GroupMapper groupMapper;
    private final EducationAuditService educationAuditService;
    
    @Transactional
    public GroupResponse createGroup(UUID currentUserId, CreateGroupRequest request) {
        Group group = groupFactory.newGroup(request.name());
        GroupResponse response = groupMapper.toResponse(groupRepository.save(group));
        educationAuditService.record(currentUserId, "GROUP_CREATED", "GROUP", response.id(), null, response);
        return response;
    }
    
    @Transactional(readOnly = true)
    public GroupResponse getGroup(UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        return groupMapper.toResponse(group);
    }

    @Transactional(readOnly = true)
    public GroupPageResponse listGroups(int page, int size, String sortBy, String direction, String q) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        String normalizedQuery = q == null ? "" : q.trim();
        Page<Group> groupPage = groupRepository.findAllByNameContainingIgnoreCaseOrderByNameAsc(normalizedQuery, pageRequest);
        return new GroupPageResponse(
                groupPage.getContent().stream().map(groupMapper::toResponse).toList(),
                groupPage.getNumber(),
                groupPage.getSize(),
                groupPage.getTotalElements(),
                groupPage.getTotalPages(),
                groupPage.isFirst(),
                groupPage.isLast()
        );
    }
    
    @Transactional(readOnly = true)
    public List<GroupMembershipResponse> getGroupsByUser(UUID userId) {
        return groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(groupStudent -> new GroupMembershipResponse(
                        groupStudent.getGroupId(),
                        groupStudent.getRole(),
                        groupStudent.getSubgroup(),
                        groupStudent.getCreatedAt(),
                        groupStudent.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupStudentUserResponse> getStudentUsersByGroup(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
        return groupStudentRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(groupStudent -> new GroupStudentUserResponse(groupStudent.getUserId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupStudentMembershipResponse> getGroupStudents(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
        List<GroupStudent> memberships = groupStudentRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId);
        Map<UUID, Long> membershipCounts = groupStudentRepository.findAllByUserIdIn(
                        memberships.stream().map(GroupStudent::getUserId).distinct().toList()
                ).stream()
                .collect(Collectors.groupingBy(GroupStudent::getUserId, Collectors.counting()));

        return memberships.stream()
                .map(membership -> toMembershipResponse(membership, membershipCounts))
                .toList();
    }

    @Transactional
    public GroupStudentMembershipResponse addStudentToGroup(
            UUID currentUserId,
            UUID groupId,
            CreateGroupStudentRequest request
    ) {
        ensureGroupExists(groupId);
        if (groupStudentRepository.existsByGroupIdAndUserId(groupId, request.userId())) {
            throw new GroupStudentAlreadyExistsException(groupId, request.userId());
        }

        GroupStudent membership = groupStudentFactory.newGroupStudent(
                groupId,
                request.userId(),
                request.role(),
                request.subgroup()
        );
        GroupStudent savedMembership = groupStudentRepository.save(membership);
        GroupStudentMembershipResponse response = toMembershipResponse(
                savedMembership,
                Map.of(request.userId(), countMemberships(request.userId()))
        );
        educationAuditService.record(currentUserId, "GROUP_STUDENT_ADDED", "GROUP_STUDENT", savedMembership.getId(), null, response);
        return response;
    }

    @Transactional
    public GroupStudentMembershipResponse updateStudentInGroup(
            UUID currentUserId,
            UUID groupId,
            UUID userId,
            UpdateGroupStudentRequest request
    ) {
        ensureGroupExists(groupId);
        GroupStudent membership = groupStudentRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new GroupStudentNotFoundException(groupId, userId));
        GroupStudentMembershipResponse oldValue = toMembershipResponse(
                membership,
                Map.of(userId, countMemberships(userId))
        );

        membership.setRole(request.role());
        membership.setSubgroup(request.subgroup());
        GroupStudent savedMembership = groupStudentRepository.save(membership);
        GroupStudentMembershipResponse response = toMembershipResponse(
                savedMembership,
                Map.of(userId, countMemberships(userId))
        );
        educationAuditService.record(currentUserId, "GROUP_STUDENT_UPDATED", "GROUP_STUDENT", savedMembership.getId(), oldValue, response);
        return response;
    }

    @Transactional
    public void removeStudentFromGroup(UUID currentUserId, UUID groupId, UUID userId) {
        ensureGroupExists(groupId);
        GroupStudent membership = groupStudentRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new GroupStudentNotFoundException(groupId, userId));
        GroupStudentMembershipResponse oldValue = toMembershipResponse(
                membership,
                Map.of(userId, countMemberships(userId))
        );
        groupStudentRepository.delete(membership);
        educationAuditService.record(currentUserId, "GROUP_STUDENT_REMOVED", "GROUP_STUDENT", membership.getId(), oldValue, null);
    }

    private void ensureGroupExists(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
    }

    private GroupStudentMembershipResponse toMembershipResponse(
            GroupStudent membership,
            Map<UUID, Long> membershipCounts
    ) {
        return new GroupStudentMembershipResponse(
                membership.getUserId(),
                membership.getRole(),
                membership.getSubgroup(),
                Math.toIntExact(membershipCounts.getOrDefault(membership.getUserId(), 1L)),
                membership.getCreatedAt(),
                membership.getUpdatedAt()
        );
    }

    private long countMemberships(UUID userId) {
        return groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(userId).size();
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

package dev.knalis.education.service.group;

import dev.knalis.education.dto.request.CreateGroupRequest;
import dev.knalis.education.dto.request.UpdateGroupRequest;
import dev.knalis.education.dto.response.GroupMembershipResponse;
import dev.knalis.education.dto.response.GroupResponse;
import dev.knalis.education.entity.Group;
import dev.knalis.education.entity.GroupSubgroupMode;
import dev.knalis.education.entity.GroupMemberRole;
import dev.knalis.education.entity.GroupStudent;
import dev.knalis.education.entity.Specialty;
import dev.knalis.education.entity.Stream;
import dev.knalis.education.entity.Subgroup;
import dev.knalis.education.exception.GroupNotFoundException;
import dev.knalis.education.exception.StreamSpecialtyYearMismatchException;
import dev.knalis.education.factory.group.GroupFactory;
import dev.knalis.education.factory.groupstudent.GroupStudentFactory;
import dev.knalis.education.mapper.GroupMapper;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.repository.SpecialtyRepository;
import dev.knalis.education.repository.StreamRepository;
import dev.knalis.education.service.common.EducationAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {
    
    @Mock
    private GroupRepository groupRepository;
    
    @Mock
    private GroupStudentRepository groupStudentRepository;

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private StreamRepository streamRepository;
    
    @Mock
    private GroupMapper groupMapper;

    @Mock
    private EducationAuditService educationAuditService;
    
    private GroupService groupService;
    
    @BeforeEach
    void setUp() {
        groupService = new GroupService(
                groupRepository,
                groupStudentRepository,
                specialtyRepository,
                streamRepository,
                new GroupFactory(),
                new GroupStudentFactory(),
                groupMapper,
                educationAuditService
        );
    }
    
    @Test
    void createGroupSavesTrimmedName() {
        UUID groupId = UUID.randomUUID();
        Instant now = Instant.now();
        Group savedGroup = new Group();
        savedGroup.setId(groupId);
        savedGroup.setName("Backend A");
        savedGroup.setSubgroupMode(GroupSubgroupMode.NONE);
        savedGroup.setCreatedAt(now);
        savedGroup.setUpdatedAt(now);
        
        GroupResponse response = new GroupResponse(groupId, "Backend A", null, null, null, GroupSubgroupMode.NONE, now, now);
        
        when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);
        when(groupMapper.toResponse(savedGroup)).thenReturn(response);
        
        GroupResponse result = groupService.createGroup(
                UUID.randomUUID(),
                new CreateGroupRequest("  Backend A  ", null, null, null, null)
        );
        
        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertEquals("Backend A", groupCaptor.getValue().getName());
        assertEquals(response, result);
    }
    
    @Test
    void getGroupThrowsWhenGroupIsMissing() {
        UUID groupId = UUID.randomUUID();
        
        when(groupRepository.findById(groupId)).thenReturn(Optional.empty());
        
        assertThrows(GroupNotFoundException.class, () -> groupService.getGroup(groupId));
    }
    
    @Test
    void getGroupsByUserReturnsMembershipsInCreatedOrder() {
        UUID userId = UUID.randomUUID();
        UUID firstGroupId = UUID.randomUUID();
        UUID secondGroupId = UUID.randomUUID();
        Instant now = Instant.now();
        
        GroupStudent firstMembership = new GroupStudent();
        firstMembership.setGroupId(firstGroupId);
        firstMembership.setUserId(userId);
        firstMembership.setRole(GroupMemberRole.STUDENT);
        firstMembership.setSubgroup(Subgroup.ALL);
        firstMembership.setCreatedAt(now);
        firstMembership.setUpdatedAt(now);
        
        GroupStudent secondMembership = new GroupStudent();
        secondMembership.setGroupId(secondGroupId);
        secondMembership.setUserId(userId);
        secondMembership.setRole(GroupMemberRole.STUDENT);
        secondMembership.setSubgroup(Subgroup.ALL);
        secondMembership.setCreatedAt(now);
        secondMembership.setUpdatedAt(now);
        
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(firstMembership, secondMembership));
        
        List<GroupMembershipResponse> result = groupService.getGroupsByUser(userId);
        
        assertEquals(List.of(
                new GroupMembershipResponse(firstGroupId, GroupMemberRole.STUDENT, Subgroup.ALL, now, now),
                new GroupMembershipResponse(secondGroupId, GroupMemberRole.STUDENT, Subgroup.ALL, now, now)
        ), result);
    }

    @Test
    void updateGroupRejectsStreamSpecialtyYearMismatch() {
        UUID groupId = UUID.randomUUID();
        UUID streamId = UUID.randomUUID();
        UUID requestedSpecialtyId = UUID.randomUUID();
        UUID streamSpecialtyId = UUID.randomUUID();
        Group group = new Group();
        group.setId(groupId);
        group.setName("G-1");
        group.setSubgroupMode(GroupSubgroupMode.NONE);

        Stream stream = new Stream();
        stream.setId(streamId);
        stream.setSpecialtyId(streamSpecialtyId);
        stream.setStudyYear(2);
        stream.setActive(true);
        Specialty specialty = new Specialty();
        specialty.setId(requestedSpecialtyId);
        specialty.setActive(true);

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(specialtyRepository.findById(requestedSpecialtyId)).thenReturn(Optional.of(specialty));
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(stream));

        assertThrows(
                StreamSpecialtyYearMismatchException.class,
                () -> groupService.updateGroup(
                        UUID.randomUUID(),
                        groupId,
                        new UpdateGroupRequest("G-1", requestedSpecialtyId, 3, streamId, GroupSubgroupMode.NONE)
                )
        );
    }
}

package dev.knalis.education.service.curriculum;

import dev.knalis.education.dto.request.CreateGroupCurriculumOverrideRequest;
import dev.knalis.education.dto.request.UpdateGroupCurriculumOverrideRequest;
import dev.knalis.education.entity.GroupCurriculumOverride;
import dev.knalis.education.exception.GroupCurriculumOverrideAlreadyExistsException;
import dev.knalis.education.repository.GroupCurriculumOverrideRepository;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupCurriculumOverrideServiceTest {

    @Mock
    private GroupCurriculumOverrideRepository overrideRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private SubjectRepository subjectRepository;

    private GroupCurriculumOverrideService service;

    @BeforeEach
    void setUp() {
        service = new GroupCurriculumOverrideService(overrideRepository, groupRepository, subjectRepository);
    }

    @Test
    void createOverrideRejectsDuplicate() {
        UUID groupId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        when(groupRepository.existsById(groupId)).thenReturn(true);
        when(subjectRepository.existsById(subjectId)).thenReturn(true);
        when(overrideRepository.existsByGroupIdAndSubjectId(groupId, subjectId)).thenReturn(true);
        assertThrows(
                GroupCurriculumOverrideAlreadyExistsException.class,
                () -> service.createGroupOverride(
                        groupId,
                        new CreateGroupCurriculumOverrideRequest(subjectId, true, null, null, null, null)
                )
        );
    }

    @Test
    void updateOverrideChangesCounts() {
        UUID groupId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        GroupCurriculumOverride override = new GroupCurriculumOverride();
        override.setId(overrideId);
        override.setGroupId(groupId);
        override.setSubjectId(UUID.randomUUID());
        when(groupRepository.existsById(groupId)).thenReturn(true);
        when(overrideRepository.findByIdAndGroupId(overrideId, groupId)).thenReturn(Optional.of(override));
        when(overrideRepository.save(any(GroupCurriculumOverride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(
                12,
                service.updateGroupOverride(
                        groupId,
                        overrideId,
                        new UpdateGroupCurriculumOverrideRequest(true, 12, 6, 4, "notes")
                ).lectureCountOverride()
        );
    }
}

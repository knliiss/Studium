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
import dev.knalis.education.factory.subject.SubjectFactory;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.repository.SubjectGroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import dev.knalis.education.service.common.EducationAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupStudentRepository groupStudentRepository;

    @Mock
    private SubjectGroupRepository subjectGroupRepository;

    @Mock
    private SubjectTeacherRepository subjectTeacherRepository;

    @Mock
    private EducationAuditService educationAuditService;

    private SubjectService subjectService;

    @BeforeEach
    void setUp() {
        subjectService = new SubjectService(
                subjectRepository,
                groupRepository,
                groupStudentRepository,
                subjectGroupRepository,
                subjectTeacherRepository,
                new SubjectFactory(),
                educationAuditService
        );
    }

    @Test
    void createSubjectWithoutGroupsWorks() {
        UUID actorId = UUID.randomUUID();
        Subject subject = subject("Math");
        when(subjectRepository.existsByNameIgnoreCase("Math")).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenReturn(subject);
        when(subjectGroupRepository.findAllBySubjectIdOrderByCreatedAtAsc(subject.getId())).thenReturn(List.of());
        when(subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subject.getId())).thenReturn(List.of());

        SubjectResponse response = subjectService.createSubject(actorId, new CreateSubjectRequest("Math", "Core"));

        assertEquals("Math", response.name());
        assertEquals(List.of(), response.groupIds());
        assertEquals(List.of(), response.teacherIds());
    }

    @Test
    void updateSubjectScalarFieldsDoesNotReplaceBindings() {
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        Subject subject = subject("Math");
        subject.setId(subjectId);
        subject.setGroupId(groupId);

        SubjectGroup subjectGroup = new SubjectGroup();
        subjectGroup.setSubjectId(subjectId);
        subjectGroup.setGroupId(groupId);
        SubjectTeacher subjectTeacher = new SubjectTeacher();
        subjectTeacher.setSubjectId(subjectId);
        subjectTeacher.setTeacherId(teacherId);

        when(subjectRepository.findWithLockingById(subjectId)).thenReturn(Optional.of(subject));
        when(subjectRepository.existsByNameIgnoreCaseAndIdNot("Networks", subjectId)).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subjectGroupRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of(subjectGroup));
        when(subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of(subjectTeacher));

        SubjectResponse response = subjectService.updateSubject(
                UUID.randomUUID(),
                subjectId,
                new UpdateSubjectRequest("Networks", "Networking")
        );

        verify(subjectGroupRepository, never()).deleteAllBySubjectId(any(UUID.class));
        verify(subjectTeacherRepository, never()).deleteAllBySubjectId(any(UUID.class));
        assertEquals(List.of(groupId), response.groupIds());
        assertEquals(List.of(teacherId), response.teacherIds());
    }

    @Test
    void updateSubjectGroupsDeduplicatesIds() {
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Subject subject = subject("Math");
        subject.setId(subjectId);

        when(subjectRepository.findWithLockingById(subjectId)).thenReturn(Optional.of(subject));
        when(subjectGroupRepository.findAllBySubjectId(subjectId)).thenReturn(List.of());
        when(groupRepository.existsById(groupId)).thenReturn(true);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subjectGroupRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());
        when(subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());

        subjectService.updateSubjectGroups(
                UUID.randomUUID(),
                subjectId,
                new UpdateSubjectGroupsRequest(List.of(groupId, groupId))
        );

        ArgumentCaptor<SubjectGroup> captor = ArgumentCaptor.forClass(SubjectGroup.class);
        verify(subjectGroupRepository).save(captor.capture());
        assertEquals(groupId, captor.getValue().getGroupId());
    }

    @Test
    void updateSubjectTeachersDeduplicatesIds() {
        UUID subjectId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        Subject subject = subject("Math");
        subject.setId(subjectId);

        when(subjectRepository.findWithLockingById(subjectId)).thenReturn(Optional.of(subject));
        when(subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subjectGroupRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());

        subjectService.updateSubjectTeachers(
                UUID.randomUUID(),
                subjectId,
                new UpdateSubjectTeachersRequest(List.of(teacherId, teacherId))
        );

        ArgumentCaptor<SubjectTeacher> captor = ArgumentCaptor.forClass(SubjectTeacher.class);
        verify(subjectTeacherRepository).save(captor.capture());
        assertEquals(teacherId, captor.getValue().getTeacherId());
    }

    @Test
    void studentListSubjectsReturnsOnlyOwnGroups() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        GroupStudent membership = new GroupStudent();
        membership.setGroupId(groupId);
        membership.setUserId(userId);

        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(membership));
        when(subjectRepository.findAllByBoundGroupIdsAndNameContainingIgnoreCase(eq(List.of(groupId)), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        SubjectPageResponse response = subjectService.listSubjects(
                userId,
                Set.of("ROLE_STUDENT"),
                0,
                20,
                "createdAt",
                "desc",
                null
        );

        assertEquals(0, response.totalElements());
    }

    @Test
    void teacherListSubjectsReturnsAssignedOnly() {
        UUID teacherId = UUID.randomUUID();
        when(subjectRepository.findAllByTeacherIdAndNameContainingIgnoreCase(eq(teacherId), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        SubjectPageResponse response = subjectService.listSubjects(
                teacherId,
                Set.of("ROLE_TEACHER"),
                0,
                20,
                "createdAt",
                "desc",
                null
        );

        assertEquals(0, response.totalElements());
    }

    @Test
    void unrelatedStudentCannotAccessSubject() {
        UUID userId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Subject subject = subject("Math");
        subject.setId(subjectId);

        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

        assertThrows(
                EducationAccessDeniedException.class,
                () -> subjectService.getSubject(userId, Set.of("ROLE_STUDENT"), subjectId)
        );
    }

    @Test
    void getSubjectsByGroupAppliesPaginationAndSorting() {
        UUID adminId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        when(groupRepository.existsById(groupId)).thenReturn(true);
        when(subjectRepository.findAllByBoundGroupId(eq(groupId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        SubjectPageResponse response = subjectService.getSubjectsByGroup(
                adminId,
                Set.of("ROLE_ADMIN"),
                groupId,
                0,
                20,
                "createdAt",
                "desc"
        );

        assertEquals(0, response.totalElements());
    }

    private Subject subject(String name) {
        Subject subject = new Subject();
        subject.setId(UUID.randomUUID());
        subject.setName(name);
        subject.setDescription("Description");
        subject.setCreatedAt(Instant.now());
        subject.setUpdatedAt(Instant.now());
        return subject;
    }
}

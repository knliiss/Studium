package dev.knalis.education.service.group;

import dev.knalis.education.dto.response.ResolvedGroupSubjectSource;
import dev.knalis.education.entity.CurriculumPlan;
import dev.knalis.education.entity.Group;
import dev.knalis.education.entity.GroupCurriculumOverride;
import dev.knalis.education.entity.GroupSubgroupMode;
import dev.knalis.education.entity.Subject;
import dev.knalis.education.entity.SubjectTeacher;
import dev.knalis.education.repository.CurriculumPlanRepository;
import dev.knalis.education.repository.GroupCurriculumOverrideRepository;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.repository.SubjectGroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupResolvedSubjectServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private SubjectGroupRepository subjectGroupRepository;
    @Mock
    private SubjectTeacherRepository subjectTeacherRepository;
    @Mock
    private GroupStudentRepository groupStudentRepository;
    @Mock
    private CurriculumPlanRepository curriculumPlanRepository;
    @Mock
    private GroupCurriculumOverrideRepository overrideRepository;

    private GroupResolvedSubjectService service;

    @BeforeEach
    void setUp() {
        service = new GroupResolvedSubjectService(
                groupRepository,
                subjectRepository,
                subjectGroupRepository,
                subjectTeacherRepository,
                groupStudentRepository,
                curriculumPlanRepository,
                overrideRepository
        );
    }

    @Test
    void resolvedSubjectsIncludeCurriculumSubjects() {
        UUID groupId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Group group = group(groupId, specialtyId, 1);
        CurriculumPlan plan = plan(specialtyId, 1, 1, subjectId, 8, 4, 2);
        Subject subject = subject(subjectId, "Math");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupStudentRepository.existsByUserIdAndGroupId(groupId, groupId)).thenReturn(true);
        when(curriculumPlanRepository.findAllBySpecialtyIdAndStudyYearAndSemesterNumberAndActiveTrue(specialtyId, 1, 1))
                .thenReturn(List.of(plan));
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(overrideRepository.findAllByGroupIdAndSubjectIdIn(groupId, Set.of(subjectId))).thenReturn(List.of());
        when(subjectRepository.findAllByBoundGroupId(groupId)).thenReturn(List.of());
        when(subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());

        var result = service.getResolvedGroupSubjects(groupId, Set.of("ROLE_STUDENT"), groupId, 1);

        assertEquals(1, result.size());
        assertEquals(ResolvedGroupSubjectSource.CURRICULUM_PLAN, result.getFirst().source());
    }

    @Test
    void resolvedSubjectsApplyDisabledOverride() {
        UUID groupId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Group group = group(groupId, specialtyId, 1);
        CurriculumPlan plan = plan(specialtyId, 1, 1, subjectId, 8, 4, 2);
        Subject subject = subject(subjectId, "Math");
        GroupCurriculumOverride override = new GroupCurriculumOverride();
        override.setGroupId(groupId);
        override.setSubjectId(subjectId);
        override.setEnabled(false);

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupStudentRepository.existsByUserIdAndGroupId(groupId, groupId)).thenReturn(true);
        when(curriculumPlanRepository.findAllBySpecialtyIdAndStudyYearAndSemesterNumberAndActiveTrue(specialtyId, 1, 1))
                .thenReturn(List.of(plan));
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(overrideRepository.findAllByGroupIdAndSubjectIdIn(groupId, Set.of(subjectId))).thenReturn(List.of(override));
        when(subjectRepository.findAllByBoundGroupId(groupId)).thenReturn(List.of());
        when(subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());

        var result = service.getResolvedGroupSubjects(groupId, Set.of("ROLE_STUDENT"), groupId, 1);

        assertEquals(true, result.getFirst().disabledByOverride());
    }

    @Test
    void disabledOverrideIsNotReplacedByDirectBinding() {
        UUID groupId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Group group = group(groupId, specialtyId, 1);
        CurriculumPlan plan = plan(specialtyId, 1, 1, subjectId, 8, 4, 2);
        Subject subject = subject(subjectId, "Math");
        GroupCurriculumOverride override = new GroupCurriculumOverride();
        override.setGroupId(groupId);
        override.setSubjectId(subjectId);
        override.setEnabled(false);

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupStudentRepository.existsByUserIdAndGroupId(groupId, groupId)).thenReturn(true);
        when(curriculumPlanRepository.findAllBySpecialtyIdAndStudyYearAndSemesterNumberAndActiveTrue(specialtyId, 1, 1))
                .thenReturn(List.of(plan));
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(overrideRepository.findAllByGroupIdAndSubjectIdIn(groupId, Set.of(subjectId))).thenReturn(List.of(override));
        when(subjectRepository.findAllByBoundGroupId(groupId)).thenReturn(List.of(subject));
        when(subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());

        var result = service.getResolvedGroupSubjects(groupId, Set.of("ROLE_STUDENT"), groupId, 1);

        assertEquals(1, result.size());
        assertEquals(ResolvedGroupSubjectSource.GROUP_OVERRIDE, result.getFirst().source());
        assertEquals(true, result.getFirst().disabledByOverride());
    }

    @Test
    void resolvedSubjectsIncludeDirectBindingsWithoutSpecialty() {
        UUID groupId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Group group = group(groupId, null, null);
        Subject subject = subject(subjectId, "Manual Subject");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupStudentRepository.existsByUserIdAndGroupId(groupId, groupId)).thenReturn(true);
        when(subjectRepository.findAllByBoundGroupId(groupId)).thenReturn(List.of(subject));
        when(subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());

        var result = service.getResolvedGroupSubjects(groupId, Set.of("ROLE_STUDENT"), groupId, 1);

        assertEquals(1, result.size());
        assertEquals(ResolvedGroupSubjectSource.DIRECT_BINDING, result.getFirst().source());
    }

    private Group group(UUID groupId, UUID specialtyId, Integer studyYear) {
        Group group = new Group();
        group.setId(groupId);
        group.setName("G1");
        group.setSpecialtyId(specialtyId);
        group.setStudyYear(studyYear);
        group.setSubgroupMode(GroupSubgroupMode.NONE);
        return group;
    }

    private CurriculumPlan plan(
            UUID specialtyId,
            int studyYear,
            int semesterNumber,
            UUID subjectId,
            int lectureCount,
            int practiceCount,
            int labCount
    ) {
        CurriculumPlan plan = new CurriculumPlan();
        plan.setSpecialtyId(specialtyId);
        plan.setStudyYear(studyYear);
        plan.setSemesterNumber(semesterNumber);
        plan.setSubjectId(subjectId);
        plan.setLectureCount(lectureCount);
        plan.setPracticeCount(practiceCount);
        plan.setLabCount(labCount);
        plan.setSupportsStreamLecture(true);
        plan.setRequiresSubgroupsForLabs(true);
        plan.setActive(true);
        return plan;
    }

    private Subject subject(UUID id, String name) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        return subject;
    }
}

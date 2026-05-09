package dev.knalis.education.service.group;

import dev.knalis.education.dto.response.ResolvedGroupSubjectResponse;
import dev.knalis.education.dto.response.ResolvedGroupSubjectSource;
import dev.knalis.education.entity.CurriculumPlan;
import dev.knalis.education.entity.Group;
import dev.knalis.education.entity.GroupCurriculumOverride;
import dev.knalis.education.entity.GroupStudent;
import dev.knalis.education.entity.Subject;
import dev.knalis.education.entity.SubjectGroup;
import dev.knalis.education.entity.SubjectTeacher;
import dev.knalis.education.exception.EducationAccessDeniedException;
import dev.knalis.education.exception.GroupNotFoundException;
import dev.knalis.education.repository.CurriculumPlanRepository;
import dev.knalis.education.repository.GroupCurriculumOverrideRepository;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.repository.SubjectGroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupResolvedSubjectService {

    private final GroupRepository groupRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectGroupRepository subjectGroupRepository;
    private final SubjectTeacherRepository subjectTeacherRepository;
    private final GroupStudentRepository groupStudentRepository;
    private final CurriculumPlanRepository curriculumPlanRepository;
    private final GroupCurriculumOverrideRepository overrideRepository;

    @Transactional(readOnly = true)
    public List<ResolvedGroupSubjectResponse> getResolvedGroupSubjects(
            UUID currentUserId,
            Set<String> currentRoles,
            UUID groupId,
            Integer semesterNumber
    ) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        ensureCanReadGroupSubjects(currentUserId, currentRoles, group);
        return resolveGroupSubjects(group, semesterNumber, true);
    }

    @Transactional(readOnly = true)
    public Set<UUID> resolveStudentSubjectIds(UUID userId) {
        List<UUID> groupIds = groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(GroupStudent::getGroupId)
                .distinct()
                .toList();
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (UUID groupId : groupIds) {
            Group group = groupRepository.findById(groupId).orElse(null);
            if (group == null) {
                continue;
            }
            resolveGroupSubjects(group, null, false).stream()
                    .filter(subject -> !subject.disabledByOverride())
                    .map(ResolvedGroupSubjectResponse::subjectId)
                    .forEach(ids::add);
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public boolean teacherCanReadGroup(UUID teacherId, UUID groupId) {
        Group group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            return false;
        }
        Set<UUID> subjectIds = resolveGroupSubjects(group, null, false).stream()
                .filter(subject -> !subject.disabledByOverride())
                .map(ResolvedGroupSubjectResponse::subjectId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (subjectIds.isEmpty()) {
            return false;
        }
        return subjectTeacherRepository.existsByTeacherIdAndSubjectIdIn(teacherId, subjectIds);
    }

    private List<ResolvedGroupSubjectResponse> resolveGroupSubjects(Group group, Integer semesterNumber, boolean includeDisabled) {
        Map<UUID, Subject> subjectById = new LinkedHashMap<>();
        LinkedHashMap<UUID, ResolvedGroupSubjectResponse> resolved = new LinkedHashMap<>();
        Set<UUID> curriculumSubjectIds = new LinkedHashSet<>();

        if (group.getSpecialtyId() != null && group.getStudyYear() != null) {
            List<CurriculumPlan> plans = semesterNumber == null
                    ? curriculumPlanRepository.findAllBySpecialtyIdAndStudyYearAndActiveTrue(
                    group.getSpecialtyId(),
                    group.getStudyYear()
            )
                    : curriculumPlanRepository.findAllBySpecialtyIdAndStudyYearAndSemesterNumberAndActiveTrue(
                    group.getSpecialtyId(),
                    group.getStudyYear(),
                    semesterNumber
            );

            for (CurriculumPlan plan : plans) {
                curriculumSubjectIds.add(plan.getSubjectId());
                Subject subject = subjectRepository.findById(plan.getSubjectId()).orElse(null);
                if (subject == null) {
                    continue;
                }
                subjectById.put(subject.getId(), subject);
                resolved.put(subject.getId(), new ResolvedGroupSubjectResponse(
                        subject.getId(),
                        subject.getName(),
                        ResolvedGroupSubjectSource.CURRICULUM_PLAN,
                        plan.getLectureCount(),
                        plan.getPracticeCount(),
                        plan.getLabCount(),
                        resolveTeacherIds(subject.getId()),
                        plan.isSupportsStreamLecture(),
                        plan.isRequiresSubgroupsForLabs(),
                        false
                ));
            }

            if (!curriculumSubjectIds.isEmpty()) {
                List<GroupCurriculumOverride> overrides = overrideRepository.findAllByGroupIdAndSubjectIdIn(
                        group.getId(),
                        curriculumSubjectIds
                );
                for (GroupCurriculumOverride override : overrides) {
                    ResolvedGroupSubjectResponse base = resolved.get(override.getSubjectId());
                    if (base == null) {
                        continue;
                    }
                    if (!override.isEnabled()) {
                        resolved.put(override.getSubjectId(), new ResolvedGroupSubjectResponse(
                                base.subjectId(),
                                base.subjectName(),
                                ResolvedGroupSubjectSource.GROUP_OVERRIDE,
                                base.lectureCount(),
                                base.practiceCount(),
                                base.labCount(),
                                base.teacherIds(),
                                base.supportsStreamLecture(),
                                base.requiresSubgroupsForLabs(),
                                true
                        ));
                        continue;
                    }
                    resolved.put(override.getSubjectId(), new ResolvedGroupSubjectResponse(
                            base.subjectId(),
                            base.subjectName(),
                            ResolvedGroupSubjectSource.GROUP_OVERRIDE,
                            override.getLectureCountOverride() == null ? base.lectureCount() : override.getLectureCountOverride(),
                            override.getPracticeCountOverride() == null ? base.practiceCount() : override.getPracticeCountOverride(),
                            override.getLabCountOverride() == null ? base.labCount() : override.getLabCountOverride(),
                            base.teacherIds(),
                            base.supportsStreamLecture(),
                            base.requiresSubgroupsForLabs(),
                            false
                    ));
                }
            }
        }

        List<Subject> directSubjects = subjectRepository.findAllByBoundGroupId(group.getId());
        for (Subject subject : directSubjects) {
            subjectById.put(subject.getId(), subject);
            ResolvedGroupSubjectResponse current = resolved.get(subject.getId());
            if (current == null || current.disabledByOverride()) {
                resolved.put(subject.getId(), new ResolvedGroupSubjectResponse(
                        subject.getId(),
                        subject.getName(),
                        ResolvedGroupSubjectSource.DIRECT_BINDING,
                        0,
                        0,
                        0,
                        resolveTeacherIds(subject.getId()),
                        false,
                        false,
                        false
                ));
            }
        }

        List<ResolvedGroupSubjectResponse> values = new ArrayList<>(resolved.values());
        if (!includeDisabled) {
            values = values.stream().filter(value -> !value.disabledByOverride()).toList();
        }
        return values.stream()
                .sorted((left, right) -> left.subjectName().compareToIgnoreCase(right.subjectName()))
                .toList();
    }

    private List<UUID> resolveTeacherIds(UUID subjectId) {
        return subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId).stream()
                .map(SubjectTeacher::getTeacherId)
                .toList();
    }

    private void ensureCanReadGroupSubjects(UUID currentUserId, Collection<String> currentRoles, Group group) {
        if (isAdmin(currentRoles)) {
            return;
        }
        if (isTeacher(currentRoles)) {
            if (teacherCanReadGroup(currentUserId, group.getId())) {
                return;
            }
            throw new EducationAccessDeniedException();
        }
        if (groupStudentRepository.existsByUserIdAndGroupId(currentUserId, group.getId())) {
            return;
        }
        throw new EducationAccessDeniedException();
    }

    private boolean isAdmin(Collection<String> roles) {
        return roles.contains("ROLE_OWNER") || roles.contains("ROLE_ADMIN");
    }

    private boolean isTeacher(Collection<String> roles) {
        return roles.contains("ROLE_TEACHER");
    }
}

package dev.knalis.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.education.dto.response.GroupCurriculumOverrideResponse;
import dev.knalis.education.entity.CurriculumPlan;
import dev.knalis.education.entity.Group;
import dev.knalis.education.entity.GroupCurriculumOverride;
import dev.knalis.education.entity.GroupMemberRole;
import dev.knalis.education.entity.GroupStudent;
import dev.knalis.education.entity.GroupSubgroupMode;
import dev.knalis.education.entity.Subject;
import dev.knalis.education.entity.SubjectGroup;
import dev.knalis.education.entity.SubjectTeacher;
import dev.knalis.education.entity.Subgroup;
import dev.knalis.education.repository.CurriculumPlanRepository;
import dev.knalis.education.repository.GroupCurriculumOverrideRepository;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.repository.SubjectGroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ResolvedSubjectsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SubjectGroupRepository subjectGroupRepository;

    @Autowired
    private SubjectTeacherRepository subjectTeacherRepository;

    @Autowired
    private CurriculumPlanRepository curriculumPlanRepository;

    @Autowired
    private GroupCurriculumOverrideRepository groupCurriculumOverrideRepository;

    @Autowired
    private GroupStudentRepository groupStudentRepository;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("app.education.jwt.public-key-path", () ->
                Path.of("infra", "keys", "public.pem").toAbsolutePath().toUri().toString());
        registry.add("app.education.internal.shared-secret", () -> "test-secret");
        registry.add("app.education.audit-service.base-url", () -> "http://localhost:8090");
        registry.add("app.education.audit-service.shared-secret", () -> "test-secret");
        registry.add("app.education.file-service.base-url", () -> "http://localhost:8083");
        registry.add("app.education.file-service.shared-secret", () -> "test-secret");
    }

    @AfterEach
    void tearDown() {
        groupCurriculumOverrideRepository.deleteAll();
        curriculumPlanRepository.deleteAll();
        subjectTeacherRepository.deleteAll();
        subjectGroupRepository.deleteAll();
        subjectRepository.deleteAll();
        groupStudentRepository.deleteAll();
        groupRepository.deleteAll();
    }

    @Test
    void groupWithoutSpecialtyReturnsDirectBindingsOnly() throws Exception {
        UUID studentId = UUID.randomUUID();
        Group group = saveGroup("SE-11", null, null);
        Subject direct = saveSubject("Direct Subject", group.getId());
        saveGroupMembership(group.getId(), studentId);

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", group.getId())
                        .queryParam("semesterNumber", "1")
                        .with(jwtFor(studentId, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subjectId").value(direct.getId().toString()))
                .andExpect(jsonPath("$[0].source").value("DIRECT_BINDING"));
    }

    @Test
    void curriculumAndDirectSubjectsAreMergedDeduplicatedAndSorted() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        Group group = saveGroup("SE-12", specialtyId, 2);
        saveGroupMembership(group.getId(), studentId);

        Subject algorithms = saveSubject("Algorithms", null);
        Subject math = saveSubject("Math", null);
        Subject zoology = saveSubject("Zoology", null);
        saveCurriculumPlan(group, algorithms.getId(), 1, 10, 6, 3, true);
        saveCurriculumPlan(group, math.getId(), 1, 12, 8, 4, true);
        saveSubjectGroup(algorithms.getId(), group.getId());
        saveSubjectGroup(zoology.getId(), group.getId());

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", group.getId())
                        .queryParam("semesterNumber", "1")
                        .with(jwtFor(studentId, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].subjectName").value("Algorithms"))
                .andExpect(jsonPath("$[1].subjectName").value("Math"))
                .andExpect(jsonPath("$[2].subjectName").value("Zoology"));
    }

    @Test
    void disabledOverrideMarksSubjectAndInactivePlanIsExcluded() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        Group group = saveGroup("SE-13", specialtyId, 1);
        saveGroupMembership(group.getId(), studentId);

        Subject activeSubject = saveSubject("Active Curriculum Subject", null);
        Subject inactiveSubject = saveSubject("Inactive Curriculum Subject", null);
        saveCurriculumPlan(group, activeSubject.getId(), 1, 8, 4, 2, true);
        saveCurriculumPlan(group, inactiveSubject.getId(), 1, 8, 4, 2, false);
        saveOverride(group.getId(), activeSubject.getId(), false, null, null, null);

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", group.getId())
                        .queryParam("semesterNumber", "1")
                        .with(jwtFor(studentId, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subjectName").value("Active Curriculum Subject"))
                .andExpect(jsonPath("$[0].disabledByOverride").value(true));
    }

    @Test
    void overrideCrudEndpointsAffectResolvedCountsAndDeleteRestoresCurriculum() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        Group group = saveGroup("SE-14", specialtyId, 1);
        saveGroupMembership(group.getId(), studentId);
        Subject subject = saveSubject("Statistics", null);
        saveCurriculumPlan(group, subject.getId(), 1, 10, 5, 2, true);

        MvcResult createResult = mockMvc.perform(post("/api/v1/education/groups/{groupId}/curriculum-overrides", group.getId())
                        .with(jwtFor(adminId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", subject.getId(),
                                "enabled", true,
                                "lectureCountOverride", 20
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        GroupCurriculumOverrideResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                GroupCurriculumOverrideResponse.class
        );

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", group.getId())
                        .queryParam("semesterNumber", "1")
                        .with(jwtFor(studentId, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lectureCount").value(20));

        mockMvc.perform(put("/api/v1/education/groups/{groupId}/curriculum-overrides/{overrideId}", group.getId(), created.id())
                        .with(jwtFor(adminId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", true,
                                "lectureCountOverride", 30
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", group.getId())
                        .queryParam("semesterNumber", "1")
                        .with(jwtFor(studentId, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lectureCount").value(30));

        mockMvc.perform(delete("/api/v1/education/groups/{groupId}/curriculum-overrides/{overrideId}", group.getId(), created.id())
                        .with(jwtFor(adminId, "ROLE_ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", group.getId())
                        .queryParam("semesterNumber", "1")
                        .with(jwtFor(studentId, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lectureCount").value(10));
    }

    @Test
    void unrelatedStudentCannotReadOtherGroupResolvedSubjects() throws Exception {
        UUID ownerStudent = UUID.randomUUID();
        UUID otherStudent = UUID.randomUUID();
        Group group = saveGroup("SE-15", null, null);
        saveGroupMembership(group.getId(), ownerStudent);
        saveSubject("Subject", group.getId());
        String requestId = "resolved-access-denied";

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", group.getId())
                        .queryParam("semesterNumber", "1")
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(otherStudent, "ROLE_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.details.exception").doesNotExist());
    }

    @Test
    void teacherAndAdminCanReadWhenRelevant() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        Group group = saveGroup("SE-16", specialtyId, 1);
        Subject subject = saveSubject("Computer Networks", null);
        saveCurriculumPlan(group, subject.getId(), 1, 6, 4, 2, true);
        saveSubjectTeacher(subject.getId(), teacherId);

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", group.getId())
                        .queryParam("semesterNumber", "1")
                        .with(jwtFor(teacherId, "ROLE_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subjectName").value("Computer Networks"));

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", group.getId())
                        .queryParam("semesterNumber", "1")
                        .with(jwtFor(adminId, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subjectName").value("Computer Networks"));
    }

    @Test
    void teacherCannotManageSpecialtyStreamCurriculumOrGroupAcademicFields() throws Exception {
        Group group = saveGroup("SE-17", null, null);

        mockMvc.perform(post("/api/v1/education/specialties")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "CS", "name", "Computer Science"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/education/streams")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "CS-Stream",
                                "specialtyId", UUID.randomUUID(),
                                "studyYear", 2
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/education/curriculum-plans")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "specialtyId", UUID.randomUUID(),
                                "studyYear", 1,
                                "semesterNumber", 1,
                                "subjectId", UUID.randomUUID(),
                                "lectureCount", 1,
                                "practiceCount", 0,
                                "labCount", 0
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/education/groups/{id}", group.getId())
                        .with(jwtFor(UUID.randomUUID(), "ROLE_TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "SE-17",
                                "subgroupMode", "NONE"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotManageGroupOverrides() throws Exception {
        Group group = saveGroup("SE-18", null, null);
        Subject subject = saveSubject("Subject", group.getId());
        GroupCurriculumOverride override = new GroupCurriculumOverride();
        override.setGroupId(group.getId());
        override.setSubjectId(subject.getId());
        override.setEnabled(true);
        GroupCurriculumOverride saved = groupCurriculumOverrideRepository.save(override);

        mockMvc.perform(put("/api/v1/education/groups/{groupId}/curriculum-overrides/{overrideId}", group.getId(), saved.getId())
                        .with(jwtFor(UUID.randomUUID(), "ROLE_STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isForbidden());
    }

    private Group saveGroup(String name, UUID specialtyId, Integer studyYear) {
        Group group = new Group();
        group.setName(name);
        group.setSpecialtyId(specialtyId);
        group.setStudyYear(studyYear);
        group.setSubgroupMode(GroupSubgroupMode.NONE);
        return groupRepository.save(group);
    }

    private Subject saveSubject(String name, UUID primaryGroupId) {
        Subject subject = new Subject();
        subject.setName(name);
        subject.setDescription("Desc");
        subject.setGroupId(primaryGroupId);
        return subjectRepository.save(subject);
    }

    private void saveSubjectGroup(UUID subjectId, UUID groupId) {
        SubjectGroup subjectGroup = new SubjectGroup();
        subjectGroup.setSubjectId(subjectId);
        subjectGroup.setGroupId(groupId);
        subjectGroupRepository.save(subjectGroup);
    }

    private void saveCurriculumPlan(
            Group group,
            UUID subjectId,
            int semesterNumber,
            int lectureCount,
            int practiceCount,
            int labCount,
            boolean active
    ) {
        CurriculumPlan plan = new CurriculumPlan();
        plan.setSpecialtyId(group.getSpecialtyId());
        plan.setStudyYear(group.getStudyYear());
        plan.setSemesterNumber(semesterNumber);
        plan.setSubjectId(subjectId);
        plan.setLectureCount(lectureCount);
        plan.setPracticeCount(practiceCount);
        plan.setLabCount(labCount);
        plan.setSupportsStreamLecture(true);
        plan.setRequiresSubgroupsForLabs(true);
        plan.setActive(active);
        curriculumPlanRepository.save(plan);
    }

    private void saveOverride(
            UUID groupId,
            UUID subjectId,
            boolean enabled,
            Integer lectureCountOverride,
            Integer practiceCountOverride,
            Integer labCountOverride
    ) {
        GroupCurriculumOverride override = new GroupCurriculumOverride();
        override.setGroupId(groupId);
        override.setSubjectId(subjectId);
        override.setEnabled(enabled);
        override.setLectureCountOverride(lectureCountOverride);
        override.setPracticeCountOverride(practiceCountOverride);
        override.setLabCountOverride(labCountOverride);
        groupCurriculumOverrideRepository.save(override);
    }

    private void saveGroupMembership(UUID groupId, UUID userId) {
        GroupStudent groupStudent = new GroupStudent();
        groupStudent.setGroupId(groupId);
        groupStudent.setUserId(userId);
        groupStudent.setRole(GroupMemberRole.STUDENT);
        groupStudent.setSubgroup(Subgroup.ALL);
        groupStudentRepository.save(groupStudent);
    }

    private void saveSubjectTeacher(UUID subjectId, UUID teacherId) {
        SubjectTeacher subjectTeacher = new SubjectTeacher();
        subjectTeacher.setSubjectId(subjectId);
        subjectTeacher.setTeacherId(teacherId);
        subjectTeacherRepository.save(subjectTeacher);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String role) {
        return jwt().jwt(jwt -> jwt
                        .subject(userId.toString())
                        .claim("username", "user")
                        .tokenValue("test-token"))
                .authorities(new SimpleGrantedAuthority(role));
    }
}

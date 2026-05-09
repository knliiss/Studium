package dev.knalis.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.education.dto.request.CreateGroupRequest;
import dev.knalis.education.dto.response.CurriculumPlanResponse;
import dev.knalis.education.dto.response.GroupCurriculumOverrideResponse;
import dev.knalis.education.dto.response.GroupResponse;
import dev.knalis.education.dto.response.ResolvedGroupSubjectResponse;
import dev.knalis.education.dto.response.ResolvedGroupSubjectSource;
import dev.knalis.education.dto.response.SpecialtyResponse;
import dev.knalis.education.dto.response.StreamResponse;
import dev.knalis.education.entity.GroupSubgroupMode;
import dev.knalis.education.exception.CurriculumPlanAlreadyExistsException;
import dev.knalis.education.exception.CurriculumPlanInvalidCountsException;
import dev.knalis.education.exception.CurriculumPlanNotFoundException;
import dev.knalis.education.exception.EducationAccessDeniedException;
import dev.knalis.education.exception.GroupCurriculumOverrideAlreadyExistsException;
import dev.knalis.education.exception.GroupCurriculumOverrideNotFoundException;
import dev.knalis.education.exception.SpecialtyCodeAlreadyExistsException;
import dev.knalis.education.exception.SpecialtyNotActiveException;
import dev.knalis.education.exception.SpecialtyNotFoundException;
import dev.knalis.education.exception.StreamNotActiveException;
import dev.knalis.education.exception.StreamNotFoundException;
import dev.knalis.education.exception.StreamSpecialtyYearMismatchException;
import dev.knalis.education.service.curriculum.CurriculumPlanService;
import dev.knalis.education.service.curriculum.GroupCurriculumOverrideService;
import dev.knalis.education.service.group.GroupResolvedSubjectService;
import dev.knalis.education.service.group.GroupService;
import dev.knalis.education.service.specialty.SpecialtyService;
import dev.knalis.education.service.stream.StreamService;
import dev.knalis.shared.security.user.CurrentUserService;
import dev.knalis.shared.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        GroupController.class,
        SpecialtyController.class,
        StreamController.class,
        CurriculumPlanController.class,
        GroupCurriculumOverrideController.class
})
@Import(GlobalExceptionHandler.class)
class AcademicStructureControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private GroupResolvedSubjectService groupResolvedSubjectService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private SpecialtyService specialtyService;

    @MockitoBean
    private StreamService streamService;

    @MockitoBean
    private CurriculumPlanService curriculumPlanService;

    @MockitoBean
    private GroupCurriculumOverrideService groupCurriculumOverrideService;

    @Test
    void ownerCanCreateSpecialty() throws Exception {
        UUID specialtyId = UUID.randomUUID();
        SpecialtyResponse response = new SpecialtyResponse(
                specialtyId,
                "CS",
                "Computer Science",
                null,
                true,
                Instant.now(),
                Instant.now()
        );
        when(specialtyService.createSpecialty(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/education/specialties")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "CS",
                                "name", "Computer Science"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(specialtyId.toString()))
                .andExpect(jsonPath("$.code").value("CS"));
    }

    @Test
    void duplicateSpecialtyCodeReturnsConflictEnvelope() throws Exception {
        String requestId = "specialty-duplicate";
        when(specialtyService.createSpecialty(any()))
                .thenThrow(new SpecialtyCodeAlreadyExistsException("CS"));

        mockMvc.perform(post("/api/v1/education/specialties")
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "CS", "name", "Computer Science"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SPECIALTY_CODE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.details.code").value("CS"));
    }

    @Test
    void missingSpecialtyReturnsNotFound() throws Exception {
        UUID specialtyId = UUID.randomUUID();
        when(specialtyService.getSpecialty(specialtyId)).thenThrow(new SpecialtyNotFoundException(specialtyId));

        mockMvc.perform(get("/api/v1/education/specialties/{id}", specialtyId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SPECIALTY_NOT_FOUND"));
    }

    @Test
    void specialtyValidationErrorReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/education/specialties")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "", "name", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fieldErrors.code").isArray())
                .andExpect(jsonPath("$.details.fieldErrors.name").isArray());
    }

    @Test
    void specialtiesActiveFilterIsPassedToService() throws Exception {
        when(specialtyService.listSpecialties(true)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/education/specialties")
                        .queryParam("active", "true")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_STUDENT")))
                .andExpect(status().isOk());

        verify(specialtyService).listSpecialties(true);
    }

    @Test
    void streamFiltersArePassedToService() throws Exception {
        UUID specialtyId = UUID.randomUUID();
        when(streamService.listStreams(eq(specialtyId), eq(2), eq(true))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/education/streams")
                        .queryParam("specialtyId", specialtyId.toString())
                        .queryParam("studyYear", "2")
                        .queryParam("active", "true")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_TEACHER")))
                .andExpect(status().isOk());

        verify(streamService).listStreams(specialtyId, 2, true);
    }

    @Test
    void missingStreamReturnsNotFoundEnvelope() throws Exception {
        UUID streamId = UUID.randomUUID();
        String requestId = "stream-not-found";
        when(streamService.getStream(streamId)).thenThrow(new StreamNotFoundException(streamId));

        mockMvc.perform(get("/api/v1/education/streams/{id}", streamId)
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STREAM_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void ownerCanUpdateGroupAcademicFields() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        UUID streamId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(any(Authentication.class))).thenReturn(ownerId);
        when(groupService.updateGroup(eq(ownerId), eq(groupId), any())).thenReturn(new GroupResponse(
                groupId,
                "SE-21",
                specialtyId,
                3,
                streamId,
                GroupSubgroupMode.TWO_SUBGROUPS,
                Instant.now(),
                Instant.now()
        ));

        mockMvc.perform(put("/api/v1/education/groups/{id}", groupId)
                        .with(jwtFor(ownerId, "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "SE-21",
                                "specialtyId", specialtyId,
                                "studyYear", 3,
                                "streamId", streamId,
                                "subgroupMode", "TWO_SUBGROUPS"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialtyId").value(specialtyId.toString()))
                .andExpect(jsonPath("$.studyYear").value(3))
                .andExpect(jsonPath("$.streamId").value(streamId.toString()))
                .andExpect(jsonPath("$.subgroupMode").value("TWO_SUBGROUPS"));
    }

    @Test
    void groupCanBeCreatedWithoutStream() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(any(Authentication.class))).thenReturn(ownerId);
        when(groupService.createGroup(eq(ownerId), any(CreateGroupRequest.class))).thenReturn(new GroupResponse(
                groupId,
                "SE-22",
                null,
                1,
                null,
                GroupSubgroupMode.NONE,
                Instant.now(),
                Instant.now()
        ));

        mockMvc.perform(post("/api/v1/education/groups")
                        .with(jwtFor(ownerId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "SE-22",
                                "studyYear", 1,
                                "subgroupMode", "NONE"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streamId").doesNotExist())
                .andExpect(jsonPath("$.studyYear").value(1));
    }

    @Test
    void groupStreamMismatchReturnsConflict() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID streamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(any(Authentication.class))).thenReturn(userId);
        when(groupService.updateGroup(eq(userId), eq(groupId), any()))
                .thenThrow(new StreamSpecialtyYearMismatchException(streamId, UUID.randomUUID(), 2));

        mockMvc.perform(put("/api/v1/education/groups/{id}", groupId)
                        .with(jwtFor(userId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "SE-21",
                                "studyYear", 2,
                                "streamId", streamId,
                                "subgroupMode", "NONE"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STREAM_SPECIALTY_YEAR_MISMATCH"));
    }

    @Test
    void inactiveSpecialtyAndStreamReturnBusinessErrors() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(any(Authentication.class))).thenReturn(userId);
        when(groupService.updateGroup(eq(userId), eq(groupId), any()))
                .thenThrow(new SpecialtyNotActiveException(specialtyId))
                .thenThrow(new StreamNotActiveException(UUID.randomUUID()));

        mockMvc.perform(put("/api/v1/education/groups/{id}", groupId)
                        .with(jwtFor(userId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "SE-21",
                                "specialtyId", specialtyId,
                                "subgroupMode", "NONE"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SPECIALTY_NOT_ACTIVE"));

        mockMvc.perform(put("/api/v1/education/groups/{id}", groupId)
                        .with(jwtFor(userId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "SE-21",
                                "streamId", UUID.randomUUID(),
                                "subgroupMode", "NONE"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STREAM_NOT_ACTIVE"));
    }

    @Test
    void invalidGroupStudyYearReturnsValidationError() throws Exception {
        UUID groupId = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/education/groups/{id}", groupId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "SE-21",
                                "studyYear", 0,
                                "subgroupMode", "NONE"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void curriculumPlanFiltersArePassedToService() throws Exception {
        UUID specialtyId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        when(curriculumPlanService.listCurriculumPlans(specialtyId, 2, 1, subjectId, true))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/education/curriculum-plans")
                        .queryParam("specialtyId", specialtyId.toString())
                        .queryParam("studyYear", "2")
                        .queryParam("semesterNumber", "1")
                        .queryParam("subjectId", subjectId.toString())
                        .queryParam("active", "true")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_TEACHER")))
                .andExpect(status().isOk());

        verify(curriculumPlanService).listCurriculumPlans(specialtyId, 2, 1, subjectId, true);
    }

    @Test
    void curriculumPlanConflictAndNotFoundAreMapped() throws Exception {
        UUID planId = UUID.randomUUID();
        when(curriculumPlanService.createCurriculumPlan(any()))
                .thenThrow(new CurriculumPlanAlreadyExistsException(UUID.randomUUID(), 2, 1, UUID.randomUUID()));
        when(curriculumPlanService.getCurriculumPlan(planId))
                .thenThrow(new CurriculumPlanNotFoundException(planId));

        mockMvc.perform(post("/api/v1/education/curriculum-plans")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "specialtyId", UUID.randomUUID(),
                                "studyYear", 2,
                                "semesterNumber", 1,
                                "subjectId", UUID.randomUUID(),
                                "lectureCount", 10,
                                "practiceCount", 0,
                                "labCount", 1
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CURRICULUM_PLAN_ALREADY_EXISTS"));

        mockMvc.perform(get("/api/v1/education/curriculum-plans/{id}", planId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CURRICULUM_PLAN_NOT_FOUND"));
    }

    @Test
    void curriculumPlanInvalidCountsAndValidationAreMapped() throws Exception {
        when(curriculumPlanService.createCurriculumPlan(any()))
                .thenThrow(new CurriculumPlanInvalidCountsException(0, 0, 0));

        mockMvc.perform(post("/api/v1/education/curriculum-plans")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "specialtyId", UUID.randomUUID(),
                                "studyYear", 2,
                                "semesterNumber", 1,
                                "subjectId", UUID.randomUUID(),
                                "lectureCount", 0,
                                "practiceCount", 0,
                                "labCount", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRICULUM_PLAN_INVALID_COUNTS"));

        mockMvc.perform(post("/api/v1/education/curriculum-plans")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "specialtyId", UUID.randomUUID(),
                                "studyYear", 2,
                                "semesterNumber", 1,
                                "subjectId", UUID.randomUUID(),
                                "lectureCount", -1,
                                "practiceCount", 0,
                                "labCount", 1
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void ownerCanCreateGroupOverride() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        GroupCurriculumOverrideResponse response = new GroupCurriculumOverrideResponse(
                overrideId,
                groupId,
                subjectId,
                true,
                10,
                6,
                4,
                null,
                Instant.now(),
                Instant.now()
        );
        when(groupCurriculumOverrideService.createGroupOverride(eq(groupId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/education/groups/{groupId}/curriculum-overrides", groupId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", subjectId,
                                "enabled", true,
                                "lectureCountOverride", 10
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(overrideId.toString()));
    }

    @Test
    void groupOverrideErrorsAreMapped() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        when(groupCurriculumOverrideService.createGroupOverride(eq(groupId), any()))
                .thenThrow(new GroupCurriculumOverrideAlreadyExistsException(groupId, subjectId));
        when(groupCurriculumOverrideService.updateGroupOverride(eq(groupId), eq(overrideId), any()))
                .thenThrow(new GroupCurriculumOverrideNotFoundException(overrideId));

        mockMvc.perform(post("/api/v1/education/groups/{groupId}/curriculum-overrides", groupId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", subjectId,
                                "enabled", true
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GROUP_CURRICULUM_OVERRIDE_ALREADY_EXISTS"));

        mockMvc.perform(put("/api/v1/education/groups/{groupId}/curriculum-overrides/{overrideId}", groupId, overrideId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GROUP_CURRICULUM_OVERRIDE_NOT_FOUND"));
    }

    @Test
    void overrideNegativeCountsReturnValidationError() throws Exception {
        UUID groupId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/education/groups/{groupId}/curriculum-overrides", groupId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", UUID.randomUUID(),
                                "enabled", true,
                                "lectureCountOverride", -1
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void resolvedSubjectsEndpointUsesSemesterParamAndSupportsSecurityEnvelope() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(any(Authentication.class))).thenReturn(userId);
        when(groupResolvedSubjectService.getResolvedGroupSubjects(eq(userId), any(), eq(groupId), eq(2)))
                .thenReturn(List.of(new ResolvedGroupSubjectResponse(
                        subjectId,
                        "Algorithms",
                        ResolvedGroupSubjectSource.CURRICULUM_PLAN,
                        12,
                        8,
                        4,
                        List.of(),
                        true,
                        true,
                        false
                )));

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", groupId)
                        .queryParam("semesterNumber", "2")
                        .with(jwtFor(userId, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subjectId").value(subjectId.toString()))
                .andExpect(jsonPath("$[0].lectureCount").value(12));

        ArgumentCaptor<Integer> semesterCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(groupResolvedSubjectService).getResolvedGroupSubjects(eq(userId), any(), eq(groupId), semesterCaptor.capture());
        assertEquals(2, semesterCaptor.getValue());
    }

    @Test
    void resolvedSubjectsDeniedAccessUsesControlledError() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String requestId = "resolved-denied";
        when(currentUserService.getCurrentUserId(any(Authentication.class))).thenReturn(userId);
        when(groupResolvedSubjectService.getResolvedGroupSubjects(eq(userId), any(), eq(groupId), eq(1)))
                .thenThrow(new EducationAccessDeniedException());

        mockMvc.perform(get("/api/v1/education/groups/{groupId}/resolved-subjects", groupId)
                        .queryParam("semesterNumber", "1")
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(userId, "ROLE_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.details.exception").doesNotExist());
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String role) {
        return jwt().jwt(jwt -> jwt
                        .subject(userId.toString())
                        .claim("username", "user")
                        .tokenValue("test-token"))
                .authorities(new SimpleGrantedAuthority(role));
    }
}

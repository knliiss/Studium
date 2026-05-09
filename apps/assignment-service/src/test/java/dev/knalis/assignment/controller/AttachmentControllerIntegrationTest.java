package dev.knalis.assignment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.education.dto.GroupMembershipResponse;
import dev.knalis.assignment.client.education.dto.SubjectResponse;
import dev.knalis.assignment.client.education.dto.TopicResponse;
import dev.knalis.assignment.client.internal.FileServiceInternalClient;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentAttachment;
import dev.knalis.assignment.entity.AssignmentGroupAvailability;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.entity.SubmissionAttachment;
import dev.knalis.assignment.exception.FileServiceUnavailableException;
import dev.knalis.assignment.repository.AssignmentAttachmentRepository;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionAttachmentRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AttachmentControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private AssignmentAttachmentRepository assignmentAttachmentRepository;

    @Autowired
    private SubmissionAttachmentRepository submissionAttachmentRepository;

    @MockitoBean
    private EducationServiceClient educationServiceClient;

    @MockitoBean
    private FileServiceClient fileServiceClient;

    @MockitoBean
    private FileServiceInternalClient fileServiceInternalClient;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("app.kafka.outbox.enabled", () -> false);
        registry.add("app.assignment.reminders.enabled", () -> false);
        registry.add("app.assignment.jwt.public-key-path", () ->
                Path.of("infra", "keys", "public.pem").toAbsolutePath().toUri().toString());
        registry.add("app.assignment.file-service.base-url", () -> "http://localhost:8083");
        registry.add("app.assignment.file-service.shared-secret", () -> "test-secret");
        registry.add("app.assignment.education-service.base-url", () -> "http://localhost:8085");
        registry.add("app.assignment.education-service.shared-secret", () -> "test-secret");
        registry.add("app.assignment.notification-service.base-url", () -> "http://localhost:8084");
        registry.add("app.assignment.notification-service.shared-secret", () -> "test-secret");
        registry.add("app.assignment.audit-service.base-url", () -> "http://localhost:8090");
        registry.add("app.assignment.audit-service.shared-secret", () -> "test-secret");
    }

    @AfterEach
    void tearDown() {
        submissionAttachmentRepository.deleteAll();
        assignmentAttachmentRepository.deleteAll();
        gradeRepository.deleteAll();
        submissionRepository.deleteAll();
        assignmentGroupAvailabilityRepository.deleteAll();
        assignmentRepository.deleteAll();
        reset(educationServiceClient, fileServiceClient, fileServiceInternalClient);
    }

    @Test
    void assignedTeacherCanAddAndRemoveAssignmentAttachment() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Assignment assignment = saveAssignment(topicId, AssignmentStatus.PUBLISHED, UUID.randomUUID());
        UUID fileId = UUID.randomUUID();

        when(educationServiceClient.getTopic(topicId)).thenReturn(topic(topicId, subjectId));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(subject(subjectId, List.of(teacherId)));
        when(fileServiceClient.getMyFile("test-token", fileId)).thenReturn(file(fileId, teacherId, "ATTACHMENT"));

        mockMvc.perform(post("/api/v1/assignments/{id}/attachments", assignment.getId())
                        .with(jwtFor(teacherId, "teacher", "ROLE_TEACHER"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", fileId, "displayName", "Task file"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignment.getId().toString()))
                .andExpect(jsonPath("$.fileId").value(fileId.toString()));

        UUID attachmentId = assignmentAttachmentRepository.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(delete("/api/v1/assignments/{id}/attachments/{attachmentId}", assignment.getId(), attachmentId)
                        .with(jwtFor(teacherId, "teacher", "ROLE_TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    void studentCannotAddAssignmentAttachmentAndGetsErrorEnvelope() throws Exception {
        UUID studentId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        String requestId = "assignment-add-denied";

        mockMvc.perform(post("/api/v1/assignments/{id}/attachments", assignment.getId())
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", UUID.randomUUID()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/assignments/" + assignment.getId() + "/attachments"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void studentCanListDownloadAndPreviewPublishedAssignmentAttachmentWhenVisible() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        saveAvailability(assignment.getId(), groupId, true, Instant.now().minusSeconds(60));
        AssignmentAttachment attachment = saveAssignmentAttachment(assignment.getId(), UUID.randomUUID(), UUID.randomUUID(), "Guide");

        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(fileServiceInternalClient.download(attachment.getFileId(), false)).thenReturn(ResponseEntity.ok("download".getBytes()));
        when(fileServiceInternalClient.download(attachment.getFileId(), true)).thenReturn(ResponseEntity.ok("preview".getBytes()));

        mockMvc.perform(get("/api/v1/assignments/{id}/attachments", assignment.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachment.getId().toString()))
                .andExpect(jsonPath("$[0].originalFileName").value("file.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].sizeBytes").value(2048));

        mockMvc.perform(get("/api/v1/assignments/{id}/attachments/{attachmentId}/download", assignment.getId(), attachment.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes("download".getBytes()));

        mockMvc.perform(get("/api/v1/assignments/{id}/attachments/{attachmentId}/preview", assignment.getId(), attachment.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes("preview".getBytes()));
    }

    @Test
    void studentDeniedForDraftAssignmentAttachmentWithRequestIdEnvelope() throws Exception {
        UUID studentId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.DRAFT, UUID.randomUUID());
        String requestId = "draft-denied";

        mockMvc.perform(get("/api/v1/assignments/{id}/attachments", assignment.getId())
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_NOT_ACCESSIBLE"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/assignments/" + assignment.getId() + "/attachments"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details.ownerId").doesNotExist());
    }

    @Test
    void studentDeniedForArchivedAssignmentAttachment() throws Exception {
        UUID studentId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.ARCHIVED, UUID.randomUUID());

        mockMvc.perform(get("/api/v1/assignments/{id}/attachments", assignment.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_NOT_ACCESSIBLE"));
    }

    @Test
    void studentDeniedForHiddenAvailabilityAssignmentAttachment() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        saveAvailability(assignment.getId(), groupId, false, Instant.now().plusSeconds(3600));
        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));

        mockMvc.perform(get("/api/v1/assignments/{id}/attachments", assignment.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_NOT_ACCESSIBLE"));
    }

    @Test
    void studentCanAccessClosedAssignmentAttachmentWhenVisible() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.CLOSED, UUID.randomUUID());
        saveAvailability(assignment.getId(), groupId, true, Instant.now().minusSeconds(60));
        AssignmentAttachment attachment = saveAssignmentAttachment(assignment.getId(), UUID.randomUUID(), UUID.randomUUID(), "Closed");
        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));

        mockMvc.perform(get("/api/v1/assignments/{id}/attachments", assignment.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachment.getId().toString()))
                .andExpect(jsonPath("$[0].originalFileName").value("file.pdf"));
    }

    @Test
    void assignmentAttachmentOwnershipBindingIsEnforced() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Assignment requestedAssignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        saveAvailability(requestedAssignment.getId(), groupId, true, Instant.now().minusSeconds(60));
        Assignment otherAssignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        AssignmentAttachment otherAttachment = saveAssignmentAttachment(otherAssignment.getId(), UUID.randomUUID(), UUID.randomUUID(), "Other");
        String requestId = "assignment-attachment-binding";
        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));

        mockMvc.perform(get("/api/v1/assignments/{id}/attachments/{attachmentId}/download", requestedAssignment.getId(), otherAttachment.getId())
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_ATTACHMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.details.ownerId").doesNotExist());
    }

    @Test
    void assignmentAttachmentDownloadReturnsControlled503WhenFileServiceUnavailable() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        saveAvailability(assignment.getId(), groupId, true, Instant.now().minusSeconds(60));
        AssignmentAttachment attachment = saveAssignmentAttachment(assignment.getId(), UUID.randomUUID(), UUID.randomUUID(), "Guide");
        String requestId = "assignment-download-unavailable";

        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(fileServiceInternalClient.download(attachment.getFileId(), false))
                .thenThrow(new FileServiceUnavailableException("download", attachment.getFileId()));

        mockMvc.perform(get("/api/v1/assignments/{id}/attachments/{attachmentId}/download", assignment.getId(), attachment.getId())
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("FILE_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void unrelatedTeacherCannotRemoveAssignmentAttachment() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Assignment assignment = saveAssignment(topicId, AssignmentStatus.PUBLISHED, UUID.randomUUID());
        AssignmentAttachment attachment = saveAssignmentAttachment(assignment.getId(), UUID.randomUUID(), UUID.randomUUID(), "Guide");
        when(educationServiceClient.getTopic(topicId)).thenReturn(topic(topicId, subjectId));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(subject(subjectId, List.of(UUID.randomUUID())));

        mockMvc.perform(delete("/api/v1/assignments/{id}/attachments/{attachmentId}", assignment.getId(), attachment.getId())
                        .with(jwtFor(teacherId, "teacher", "ROLE_TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_FILE_ACCESS_DENIED"));
    }

    @Test
    void studentCanManageOwnSubmissionAttachmentsWhenAssignmentIsOpen() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        saveAvailability(assignment.getId(), groupId, true, Instant.now().plusSeconds(3600));
        Submission submission = saveSubmission(assignment.getId(), studentId, UUID.randomUUID());
        SubmissionAttachment primaryAttachment = saveSubmissionAttachment(submission.getId(), submission.getFileId(), studentId, "Primary");
        SubmissionAttachment removableAttachment = saveSubmissionAttachment(submission.getId(), UUID.randomUUID(), studentId, "Removable");
        UUID newFileId = UUID.randomUUID();

        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(fileServiceClient.getMyFile("test-token", newFileId)).thenReturn(file(newFileId, studentId, "ATTACHMENT"));
        when(fileServiceInternalClient.download(primaryAttachment.getFileId(), false)).thenReturn(ResponseEntity.ok("download".getBytes()));
        when(fileServiceInternalClient.download(primaryAttachment.getFileId(), true)).thenReturn(ResponseEntity.ok("preview".getBytes()));

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/attachments", submission.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", newFileId, "displayName", "Solution v2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(submission.getId().toString()))
                .andExpect(jsonPath("$.fileId").value(newFileId.toString()));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments", submission.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments/{attachmentId}/download", submission.getId(), primaryAttachment.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments/{attachmentId}/preview", submission.getId(), primaryAttachment.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/submissions/{submissionId}/attachments/{attachmentId}", submission.getId(), removableAttachment.getId())
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void otherStudentCannotAccessOrModifySubmissionAttachments() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID intruderId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        Submission submission = saveSubmission(assignment.getId(), ownerId, UUID.randomUUID());
        SubmissionAttachment attachment = saveSubmissionAttachment(submission.getId(), submission.getFileId(), ownerId, "Owner file");
        String requestId = "submission-denied";

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments", submission.getId())
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(intruderId, "intruder", "ROLE_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SUBMISSION_NOT_ACCESSIBLE"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/submissions/" + submission.getId() + "/attachments"))
                .andExpect(jsonPath("$.details.ownerId").doesNotExist());

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/attachments", submission.getId())
                        .with(jwtFor(intruderId, "intruder", "ROLE_STUDENT"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", UUID.randomUUID()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SUBMISSION_FILE_ACCESS_DENIED"));

        mockMvc.perform(delete("/api/v1/submissions/{submissionId}/attachments/{attachmentId}", submission.getId(), attachment.getId())
                        .with(jwtFor(intruderId, "intruder", "ROLE_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SUBMISSION_FILE_ACCESS_DENIED"));
    }

    @Test
    void assignedTeacherCanReviewSubmissionAttachmentsButUnrelatedTeacherIsDenied() throws Exception {
        UUID assignedTeacherId = UUID.randomUUID();
        UUID unrelatedTeacherId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Assignment assignment = saveAssignment(topicId, AssignmentStatus.PUBLISHED, UUID.randomUUID());
        Submission submission = saveSubmission(assignment.getId(), UUID.randomUUID(), UUID.randomUUID());
        SubmissionAttachment attachment = saveSubmissionAttachment(submission.getId(), submission.getFileId(), submission.getUserId(), "Submission");

        when(educationServiceClient.getTopic(topicId)).thenReturn(topic(topicId, subjectId));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(subject(subjectId, List.of(assignedTeacherId)));
        when(fileServiceInternalClient.download(attachment.getFileId(), false)).thenReturn(ResponseEntity.ok("download".getBytes()));
        when(fileServiceInternalClient.download(attachment.getFileId(), true)).thenReturn(ResponseEntity.ok("preview".getBytes()));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments", submission.getId())
                        .with(jwtFor(assignedTeacherId, "teacher", "ROLE_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachment.getId().toString()))
                .andExpect(jsonPath("$[0].originalFileName").value("file.pdf"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments/{attachmentId}/download", submission.getId(), attachment.getId())
                        .with(jwtFor(assignedTeacherId, "teacher", "ROLE_TEACHER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments/{attachmentId}/preview", submission.getId(), attachment.getId())
                        .with(jwtFor(assignedTeacherId, "teacher", "ROLE_TEACHER")))
                .andExpect(status().isOk());

        when(educationServiceClient.getSubject(subjectId)).thenReturn(subject(subjectId, List.of(UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments", submission.getId())
                        .with(jwtFor(unrelatedTeacherId, "teacher", "ROLE_TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SUBMISSION_NOT_ACCESSIBLE"));
    }

    @Test
    void adminCanAccessSubmissionAttachments() throws Exception {
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.ARCHIVED, UUID.randomUUID());
        Submission submission = saveSubmission(assignment.getId(), UUID.randomUUID(), UUID.randomUUID());
        SubmissionAttachment attachment = saveSubmissionAttachment(submission.getId(), submission.getFileId(), submission.getUserId(), "Archived");
        when(fileServiceInternalClient.download(attachment.getFileId(), false)).thenReturn(ResponseEntity.ok("download".getBytes()));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments", submission.getId())
                        .with(jwtFor(UUID.randomUUID(), "admin", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachment.getId().toString()))
                .andExpect(jsonPath("$[0].originalFileName").value("file.pdf"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments/{attachmentId}/download", submission.getId(), attachment.getId())
                        .with(jwtFor(UUID.randomUUID(), "admin", "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void submissionAttachmentOwnershipBindingIsEnforced() throws Exception {
        UUID studentId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        Submission requestedSubmission = saveSubmission(assignment.getId(), studentId, UUID.randomUUID());
        Submission otherSubmission = saveSubmission(assignment.getId(), studentId, UUID.randomUUID());
        SubmissionAttachment otherAttachment = saveSubmissionAttachment(otherSubmission.getId(), UUID.randomUUID(), studentId, "Other");
        String requestId = "submission-binding";

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments/{attachmentId}/download", requestedSubmission.getId(), otherAttachment.getId())
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SUBMISSION_ATTACHMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.details.ownerId").doesNotExist());
    }

    @Test
    void submissionAttachmentPreviewReturnsControlled503WhenFileServiceUnavailable() throws Exception {
        UUID studentId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.PUBLISHED, UUID.randomUUID());
        Submission submission = saveSubmission(assignment.getId(), studentId, UUID.randomUUID());
        SubmissionAttachment attachment = saveSubmissionAttachment(submission.getId(), submission.getFileId(), studentId, "Primary");
        String requestId = "submission-preview-unavailable";

        when(fileServiceInternalClient.download(attachment.getFileId(), true))
                .thenThrow(new FileServiceUnavailableException("preview", attachment.getFileId()));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/attachments/{attachmentId}/preview", submission.getId(), attachment.getId())
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("FILE_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void closedAssignmentBlocksSubmissionAttachmentAddWithBusinessError() throws Exception {
        UUID studentId = UUID.randomUUID();
        Assignment assignment = saveAssignment(UUID.randomUUID(), AssignmentStatus.CLOSED, UUID.randomUUID());
        Submission submission = saveSubmission(assignment.getId(), studentId, UUID.randomUUID());
        UUID fileId = UUID.randomUUID();
        String requestId = "submission-closed";

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/attachments", submission.getId())
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(studentId, "student", "ROLE_STUDENT"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", fileId, "displayName", "Late upload"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_CLOSED"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errorCode").value(org.hamcrest.Matchers.not("INTERNAL_ERROR")));
    }

    private Assignment saveAssignment(UUID topicId, AssignmentStatus status, UUID createdByUserId) {
        Assignment assignment = new Assignment();
        assignment.setTopicId(topicId);
        assignment.setCreatedByUserId(createdByUserId);
        assignment.setTitle("Assignment");
        assignment.setDescription("Description");
        assignment.setDeadline(Instant.now().plusSeconds(7200));
        assignment.setAllowLateSubmissions(true);
        assignment.setAllowResubmit(true);
        assignment.setMaxSubmissions(5);
        assignment.setMaxPoints(100);
        assignment.setStatus(status);
        return assignmentRepository.save(assignment);
    }

    private AssignmentGroupAvailability saveAvailability(UUID assignmentId, UUID groupId, boolean visible, Instant deadline) {
        AssignmentGroupAvailability availability = new AssignmentGroupAvailability();
        availability.setAssignmentId(assignmentId);
        availability.setGroupId(groupId);
        availability.setVisible(visible);
        availability.setAvailableFrom(Instant.now().minusSeconds(600));
        availability.setDeadline(deadline);
        availability.setAllowLateSubmissions(true);
        availability.setAllowResubmit(true);
        availability.setMaxSubmissions(5);
        return assignmentGroupAvailabilityRepository.save(availability);
    }

    private AssignmentAttachment saveAssignmentAttachment(UUID assignmentId, UUID fileId, UUID uploadedByUserId, String displayName) {
        AssignmentAttachment attachment = new AssignmentAttachment();
        attachment.setAssignmentId(assignmentId);
        attachment.setFileId(fileId);
        attachment.setUploadedByUserId(uploadedByUserId);
        attachment.setDisplayName(displayName);
        attachment.setOriginalFileName("file.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytes(2048L);
        return assignmentAttachmentRepository.save(attachment);
    }

    private Submission saveSubmission(UUID assignmentId, UUID userId, UUID fileId) {
        Submission submission = new Submission();
        submission.setAssignmentId(assignmentId);
        submission.setUserId(userId);
        submission.setFileId(fileId);
        return submissionRepository.save(submission);
    }

    private SubmissionAttachment saveSubmissionAttachment(UUID submissionId, UUID fileId, UUID uploadedByUserId, String displayName) {
        SubmissionAttachment attachment = new SubmissionAttachment();
        attachment.setSubmissionId(submissionId);
        attachment.setFileId(fileId);
        attachment.setUploadedByUserId(uploadedByUserId);
        attachment.setDisplayName(displayName);
        attachment.setOriginalFileName("file.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytes(2048L);
        return submissionAttachmentRepository.save(attachment);
    }

    private TopicResponse topic(UUID topicId, UUID subjectId) {
        Instant now = Instant.now();
        return new TopicResponse(topicId, subjectId, "Topic", 0, now, now);
    }

    private SubjectResponse subject(UUID subjectId, List<UUID> teacherIds) {
        Instant now = Instant.now();
        return new SubjectResponse(subjectId, "Subject", UUID.randomUUID(), List.of(), teacherIds, null, now, now);
    }

    private RemoteStoredFileResponse file(UUID fileId, UUID ownerId, String fileKind) {
        Instant now = Instant.now();
        return new RemoteStoredFileResponse(
                fileId,
                ownerId,
                "file.pdf",
                "application/pdf",
                2048L,
                fileKind,
                "PRIVATE",
                "ACTIVE",
                now.toString(),
                now.toString(),
                now.toString()
        );
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(
            UUID userId,
            String username,
            String... authorities
    ) {
                return jwt().jwt(jwt -> jwt
                        .subject(userId.toString())
                        .claim("username", username)
                        .tokenValue("test-token"))
                .authorities(Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}

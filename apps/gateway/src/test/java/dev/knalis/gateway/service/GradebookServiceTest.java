package dev.knalis.gateway.service;

import dev.knalis.gateway.client.assignment.AssignmentServiceClient;
import dev.knalis.gateway.client.auth.AuthServiceClient;
import dev.knalis.gateway.client.auth.dto.UserSummaryResponse;
import dev.knalis.gateway.client.education.EducationServiceClient;
import dev.knalis.gateway.client.education.dto.GroupPageResponse;
import dev.knalis.gateway.client.education.dto.GroupResponse;
import dev.knalis.gateway.client.education.dto.GroupStudentMembershipResponse;
import dev.knalis.gateway.client.education.dto.SubjectPageResponse;
import dev.knalis.gateway.client.education.dto.SubjectResponse;
import dev.knalis.gateway.client.testing.TestingServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradebookServiceTest {

    @Mock
    private AssignmentServiceClient assignmentServiceClient;

    @Mock
    private TestingServiceClient testingServiceClient;

    @Mock
    private EducationServiceClient educationServiceClient;

    @Mock
    private AuthServiceClient authServiceClient;

    private GradebookService gradebookService;

    @BeforeEach
    void setUp() {
        gradebookService = new GradebookService(
                assignmentServiceClient,
                testingServiceClient,
                educationServiceClient,
                authServiceClient
        );
    }

    @Test
    void ownerExportOverviewWithoutFiltersReturnsCsv() {
        GradebookFixture fixture = stubSingleSubjectFixture();

        ResponseEntity<byte[]> response = gradebookService.exportGradebook(
                UUID.randomUUID(),
                Set.of("ROLE_OWNER"),
                "token",
                "request-id",
                null,
                null,
                null,
                null
        ).block();

        assertCsvResponse(response, "gradebook-admin-overview-");
        String csv = csvBody(response);
        assertTrue(csv.startsWith("subject,group,teachers,students,averagePercent,high,good,average,low,missing,completionPercent"));
        assertTrue(csv.contains("\"Algorithms\""));
        assertTrue(csv.contains("\"KN-101\""));
    }

    @Test
    void ownerExportDetailWithFiltersReturnsMatrixCsv() {
        GradebookFixture fixture = stubSingleSubjectFixture();

        ResponseEntity<byte[]> response = gradebookService.exportGradebook(
                UUID.randomUUID(),
                Set.of("ROLE_OWNER"),
                "token",
                "request-id",
                fixture.subjectId(),
                fixture.groupId(),
                null,
                null
        ).block();

        assertCsvResponse(response, "gradebook-admin-detail-");
        String csv = csvBody(response);
        assertTrue(csv.startsWith("student,averagePercent,missingCount"));
        assertTrue(csv.contains("\"student.alpha\""));
    }

    @Test
    void ownerExportOverviewWithSubjectOnlyReturnsFilteredCsv() {
        GradebookFixture fixture = stubTwoSubjectOverviewFixture();

        ResponseEntity<byte[]> response = gradebookService.exportGradebook(
                UUID.randomUUID(),
                Set.of("ROLE_OWNER"),
                "token",
                "request-id",
                fixture.subjectId(),
                null,
                null,
                "ALL"
        ).block();

        assertCsvResponse(response, "gradebook-admin-overview-");
        String csv = csvBody(response);
        assertTrue(csv.contains("\"Algorithms\""));
        assertTrue(!csv.contains("\"Databases\""));
    }

    @Test
    void ownerExportOverviewWithGroupOnlyReturnsFilteredCsv() {
        GradebookFixture fixture = stubTwoGroupOverviewFixture();

        ResponseEntity<byte[]> response = gradebookService.exportGradebook(
                UUID.randomUUID(),
                Set.of("ROLE_OWNER"),
                "token",
                "request-id",
                null,
                fixture.groupId(),
                null,
                "ALL"
        ).block();

        assertCsvResponse(response, "gradebook-admin-overview-");
        String csv = csvBody(response);
        assertTrue(csv.contains("\"KN-101\""));
        assertTrue(!csv.contains("\"KN-202\""));
    }

    @Test
    void teacherExportWithSubjectAndGroupReturnsMatrixCsv() {
        GradebookFixture fixture = stubSingleSubjectFixture();

        ResponseEntity<byte[]> response = gradebookService.exportGradebook(
                fixture.teacherId(),
                Set.of("ROLE_TEACHER"),
                "token",
                "request-id",
                fixture.subjectId(),
                fixture.groupId(),
                null,
                "ALL"
        ).block();

        assertCsvResponse(response, "gradebook-teacher-");
        assertTrue(csvBody(response).startsWith("student,averagePercent,missingCount"));
    }

    @Test
    void studentExportReturnsOwnGradebookCsv() {
        GradebookFixture fixture = stubStudentFixture();

        ResponseEntity<byte[]> response = gradebookService.exportGradebook(
                fixture.studentId(),
                Set.of("ROLE_STUDENT"),
                "token",
                "request-id",
                null,
                null,
                null,
                null
        ).block();

        assertCsvResponse(response, "gradebook-student-");
        assertTrue(csvBody(response).startsWith("subject,teacher,activityType,title,status,score,maxPoints,percentage,dueAt"));
    }

    @Test
    void emptyOverviewExportDoesNotFail() {
        when(educationServiceClient.listSubjects("token", "request-id", 200))
                .thenReturn(Mono.just(new SubjectPageResponse(List.of(), 0, 200, 0, 0, true, true)));
        when(educationServiceClient.listGroups("token", "request-id", 200))
                .thenReturn(Mono.just(new GroupPageResponse(List.of(), 0, 200, 0, 0, true, true)));

        ResponseEntity<byte[]> response = gradebookService.exportGradebook(
                UUID.randomUUID(),
                Set.of("ROLE_ADMIN"),
                "token",
                "request-id",
                null,
                null,
                null,
                null
        ).block();

        assertCsvResponse(response, "gradebook-admin-overview-");
        assertEquals("subject,group,teachers,students,averagePercent,high,good,average,low,missing,completionPercent\n", csvBody(response));
        verifyNoInteractions(authServiceClient);
    }

    @Test
    void nullCsvCellsExportAsEmptyValues() {
        stubSingleSubjectFixture();

        ResponseEntity<byte[]> response = gradebookService.exportGradebook(
                UUID.randomUUID(),
                Set.of("ROLE_OWNER"),
                "token",
                "request-id",
                null,
                null,
                null,
                null
        ).block();

        assertCsvResponse(response, "gradebook-admin-overview-");
        String csv = csvBody(response);
        assertTrue(csv.contains("\"Algorithms\""));
        assertTrue(csv.contains(",,\"0\",\"0\""));
    }

    private GradebookFixture stubSingleSubjectFixture() {
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        SubjectResponse subject = new SubjectResponse(
                subjectId,
                "Algorithms",
                groupId,
                List.of(groupId),
                List.of(teacherId),
                "Core algorithms"
        );
        GroupResponse group = new GroupResponse(groupId, "KN-101", 1);
        GroupStudentMembershipResponse membership = new GroupStudentMembershipResponse(studentId, "STUDENT", "ALL", 1);

        when(educationServiceClient.listSubjects("token", "request-id", 200))
                .thenReturn(Mono.just(new SubjectPageResponse(List.of(subject), 0, 200, 1, 1, true, true)));
        when(educationServiceClient.listGroups("token", "request-id", 200))
                .thenReturn(Mono.just(new GroupPageResponse(List.of(group), 0, 200, 1, 1, true, true)));
        when(educationServiceClient.getGroup("token", "request-id", groupId))
                .thenReturn(Mono.just(group));
        when(educationServiceClient.getGroupStudents("token", "request-id", groupId))
                .thenReturn(Mono.just(List.of(membership)));
        when(educationServiceClient.getTopicsBySubject("token", "request-id", subjectId))
                .thenReturn(Mono.just(List.of()));
        when(authServiceClient.lookupUsers(eq("token"), eq("request-id"), any()))
                .thenReturn(Mono.just(List.of(
                        new UserSummaryResponse(teacherId, "teacher.one", "teacher@example.com", Set.of("ROLE_TEACHER")),
                        new UserSummaryResponse(studentId, "student.alpha", "student@example.com", Set.of("ROLE_STUDENT"))
                )));

        return new GradebookFixture(subjectId, groupId, teacherId, studentId);
    }

    private GradebookFixture stubStudentFixture() {
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        SubjectResponse subject = new SubjectResponse(
                subjectId,
                "Algorithms",
                groupId,
                List.of(groupId),
                List.of(teacherId),
                "Core algorithms"
        );
        GroupResponse group = new GroupResponse(groupId, "KN-101", 1);

        when(educationServiceClient.listSubjects("token", "request-id", 200))
                .thenReturn(Mono.just(new SubjectPageResponse(List.of(subject), 0, 200, 1, 1, true, true)));
        when(educationServiceClient.listGroups("token", "request-id", 200))
                .thenReturn(Mono.just(new GroupPageResponse(List.of(group), 0, 200, 1, 1, true, true)));
        when(educationServiceClient.getTopicsBySubject("token", "request-id", subjectId))
                .thenReturn(Mono.just(List.of()));
        when(authServiceClient.lookupUsers(eq("token"), eq("request-id"), any()))
                .thenReturn(Mono.just(List.of(
                        new UserSummaryResponse(teacherId, "teacher.one", "teacher@example.com", Set.of("ROLE_TEACHER"))
                )));

        return new GradebookFixture(subjectId, groupId, teacherId, studentId);
    }

    private GradebookFixture stubTwoSubjectOverviewFixture() {
        UUID subjectId = UUID.randomUUID();
        UUID secondSubjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        SubjectResponse firstSubject = new SubjectResponse(
                subjectId,
                "Algorithms",
                groupId,
                List.of(groupId),
                List.of(teacherId),
                "Core algorithms"
        );
        SubjectResponse secondSubject = new SubjectResponse(
                secondSubjectId,
                "Databases",
                groupId,
                List.of(groupId),
                List.of(teacherId),
                "Relational systems"
        );
        GroupResponse group = new GroupResponse(groupId, "KN-101", 1);
        GroupStudentMembershipResponse membership = new GroupStudentMembershipResponse(studentId, "STUDENT", "ALL", 1);

        when(educationServiceClient.listSubjects("token", "request-id", 200))
                .thenReturn(Mono.just(new SubjectPageResponse(List.of(firstSubject, secondSubject), 0, 200, 2, 1, true, true)));
        when(educationServiceClient.listGroups("token", "request-id", 200))
                .thenReturn(Mono.just(new GroupPageResponse(List.of(group), 0, 200, 1, 1, true, true)));
        when(educationServiceClient.getGroup("token", "request-id", groupId))
                .thenReturn(Mono.just(group));
        when(educationServiceClient.getGroupStudents("token", "request-id", groupId))
                .thenReturn(Mono.just(List.of(membership)));
        when(educationServiceClient.getTopicsBySubject("token", "request-id", subjectId))
                .thenReturn(Mono.just(List.of()));
        when(educationServiceClient.getTopicsBySubject("token", "request-id", secondSubjectId))
                .thenReturn(Mono.just(List.of()));
        when(authServiceClient.lookupUsers(eq("token"), eq("request-id"), any()))
                .thenReturn(Mono.just(List.of(
                        new UserSummaryResponse(teacherId, "teacher.one", "teacher@example.com", Set.of("ROLE_TEACHER")),
                        new UserSummaryResponse(studentId, "student.alpha", "student@example.com", Set.of("ROLE_STUDENT"))
                )));

        return new GradebookFixture(subjectId, groupId, teacherId, studentId);
    }

    private GradebookFixture stubTwoGroupOverviewFixture() {
        UUID subjectId = UUID.randomUUID();
        UUID firstGroupId = UUID.randomUUID();
        UUID secondGroupId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        SubjectResponse subject = new SubjectResponse(
                subjectId,
                "Algorithms",
                firstGroupId,
                List.of(firstGroupId, secondGroupId),
                List.of(teacherId),
                "Core algorithms"
        );
        GroupResponse firstGroup = new GroupResponse(firstGroupId, "KN-101", 1);
        GroupResponse secondGroup = new GroupResponse(secondGroupId, "KN-202", 2);
        GroupStudentMembershipResponse membership = new GroupStudentMembershipResponse(studentId, "STUDENT", "ALL", 1);

        when(educationServiceClient.listSubjects("token", "request-id", 200))
                .thenReturn(Mono.just(new SubjectPageResponse(List.of(subject), 0, 200, 1, 1, true, true)));
        when(educationServiceClient.listGroups("token", "request-id", 200))
                .thenReturn(Mono.just(new GroupPageResponse(List.of(firstGroup, secondGroup), 0, 200, 2, 1, true, true)));
        when(educationServiceClient.getGroup("token", "request-id", firstGroupId))
                .thenReturn(Mono.just(firstGroup));
        when(educationServiceClient.getGroup("token", "request-id", secondGroupId))
                .thenReturn(Mono.just(secondGroup));
        when(educationServiceClient.getGroupStudents("token", "request-id", firstGroupId))
                .thenReturn(Mono.just(List.of(membership)));
        when(educationServiceClient.getGroupStudents("token", "request-id", secondGroupId))
                .thenReturn(Mono.just(List.of(membership)));
        when(educationServiceClient.getTopicsBySubject("token", "request-id", subjectId))
                .thenReturn(Mono.just(List.of()));
        when(authServiceClient.lookupUsers(eq("token"), eq("request-id"), any()))
                .thenReturn(Mono.just(List.of(
                        new UserSummaryResponse(teacherId, "teacher.one", "teacher@example.com", Set.of("ROLE_TEACHER")),
                        new UserSummaryResponse(studentId, "student.alpha", "student@example.com", Set.of("ROLE_STUDENT"))
                )));

        return new GradebookFixture(subjectId, firstGroupId, teacherId, studentId);
    }

    private void assertCsvResponse(ResponseEntity<byte[]> response, String filenamePrefix) {
        assertNotNull(response);
        assertEquals(new MediaType("text", "csv", StandardCharsets.UTF_8), response.getHeaders().getContentType());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.contains("attachment;"));
        assertTrue(disposition.contains(filenamePrefix));
        assertNotNull(response.getBody());
    }

    private String csvBody(ResponseEntity<byte[]> response) {
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }

    private record GradebookFixture(
            UUID subjectId,
            UUID groupId,
            UUID teacherId,
            UUID studentId
    ) {
    }
}

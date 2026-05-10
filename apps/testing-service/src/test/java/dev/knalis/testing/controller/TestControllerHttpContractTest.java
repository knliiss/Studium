package dev.knalis.testing.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.shared.security.user.CurrentUserService;
import dev.knalis.testing.dto.request.QuestionAnswerSubmissionRequest;
import dev.knalis.testing.dto.request.SubmitTestAttemptRequest;
import dev.knalis.testing.dto.response.TestPreviewViewResponse;
import dev.knalis.testing.dto.response.TestQuestionAnswerResponse;
import dev.knalis.testing.dto.response.TestQuestionViewResponse;
import dev.knalis.testing.dto.response.TestResponse;
import dev.knalis.testing.dto.response.TestResultResponse;
import dev.knalis.testing.dto.response.TestStudentAnswerOptionResponse;
import dev.knalis.testing.dto.response.TestStudentQuestionViewResponse;
import dev.knalis.testing.dto.response.TestStudentViewResponse;
import dev.knalis.testing.entity.QuestionType;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.service.result.TestResultService;
import dev.knalis.testing.service.test.TestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestController.class)
@AutoConfigureMockMvc(addFilters = false)
class TestControllerHttpContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TestService testService;

    @MockBean
    private TestResultService testResultService;

    @MockBean
    private CurrentUserService currentUserService;

    private UUID currentUserId;
    private UUID testId;
    private TestResultResponse defaultResultResponse;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        testId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(any())).thenReturn(currentUserId);
        defaultResultResponse = new TestResultResponse(
                UUID.randomUUID(),
                testId,
                currentUserId,
                UUID.randomUUID(),
                42,
                42,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        when(testResultService.submitTestAttempt(eq(currentUserId), eq(testId), any())).thenReturn(defaultResultResponse);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void finishAcceptsCanonicalPayloadShapesOverHttp() throws Exception {
        String body = """
                {
                  "answers": [
                    {"questionId":"%s","value":"%s"},
                    {"questionId":"%s","value":["%s","%s"]},
                    {"questionId":"%s","value":"%s"},
                    {"questionId":"%s","value":"short"},
                    {"questionId":"%s","value":42},
                    {"questionId":"%s","value":"42"},
                    {"questionId":"%s","value":{"l1":"r1","l2":"r2"}},
                    {"questionId":"%s","value":["i1","i2"]},
                    {"questionId":"%s","value":{"b1":"x","b2":"y"}},
                    {"questionId":"%s","value":"long answer"},
                    {"questionId":"%s","value":{"fileId":"f-1"}},
                    {"questionId":"%s","value":{"manual":"answer"}}
                  ]
                }
                """.formatted(
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/testing/tests/{id}/finish", testId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(42));

        ArgumentCaptor<SubmitTestAttemptRequest> captor = ArgumentCaptor.forClass(SubmitTestAttemptRequest.class);
        verify(testResultService).submitTestAttempt(eq(currentUserId), eq(testId), captor.capture());
        List<QuestionAnswerSubmissionRequest> answers = captor.getValue().answers();
        assertEquals(12, answers.size());
        assertTrue(answers.get(0).value().isTextual());
        assertTrue(answers.get(1).value().isArray());
        assertTrue(answers.get(4).value().isNumber());
        assertTrue(answers.get(5).value().isTextual());
        assertTrue(answers.get(6).value().isObject());
        assertTrue(answers.get(7).value().isArray());
        assertTrue(answers.get(8).value().isObject());
        assertTrue(answers.get(10).value().isObject());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void startAndFinishAreAccessibleForStudentRole() throws Exception {
        mockMvc.perform(post("/api/v1/testing/tests/{id}/start", testId).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/testing/tests/{id}/finish", testId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void finishAcceptsMalformedPayloadWithoutServerError() throws Exception {
        mockMvc.perform(post("/api/v1/testing/tests/{id}/finish", testId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":\"%s\",\"value\":{}}]}".formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentViewHttpJsonDoesNotLeakSensitiveFieldsAndContainsSafePresentation() throws Exception {
        when(testService.getStudentView(eq(currentUserId), eq(testId))).thenReturn(studentViewResponse());

        String response = mockMvc.perform(get("/api/v1/testing/tests/{id}/student-view", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String raw = json.toString();
        assertTrue(raw.contains("leftItems"));
        assertTrue(raw.contains("rightItems"));
        assertTrue(raw.contains("blanks"));
        assertTrue(raw.contains("items"));
        assertTrue(raw.contains("allowedFileTypes"));
        assertTrue(raw.contains("unit"));
        assertTrue(!raw.contains("isCorrect"));
        assertTrue(!raw.contains("configurationJson"));
        assertTrue(!raw.contains("correctValue"));
        assertTrue(!raw.contains("tolerance"));
        assertTrue(!raw.contains("acceptedAnswers"));
        assertTrue(!raw.contains("rubric"));
        assertTrue(!raw.contains("\"feedback\""));
    }

    @Test
    void previewAllowsTeacherAndDoesNotCreateAttemptOrFinish() throws Exception {
        when(testService.getPreviewView(eq(currentUserId), any(Boolean.class), eq(testId))).thenReturn(previewViewResponse());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "teacher",
                "n/a",
                List.of(() -> "ROLE_TEACHER")
        );

        mockMvc.perform(get("/api/v1/testing/tests/{id}/preview", testId).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value(true))
                .andExpect(jsonPath("$.questions[0].answers[0].isCorrect").value(true));

        verify(testService, never()).startTest(any(), any());
        verify(testResultService, never()).submitTestAttempt(any(), any(), any());
    }

    private TestStudentViewResponse studentViewResponse() {
        TestResponse test = new TestResponse(
                testId,
                UUID.randomUUID(),
                "Sorting Quiz",
                0,
                TestStatus.PUBLISHED,
                1,
                100,
                30,
                null,
                null,
                false,
                false,
                false,
                Instant.now(),
                Instant.now()
        );
        TestStudentQuestionViewResponse question = new TestStudentQuestionViewResponse(
                UUID.randomUUID(),
                testId,
                "Match items",
                QuestionType.MATCHING,
                "Description",
                10,
                0,
                true,
                List.of(new TestStudentAnswerOptionResponse(UUID.randomUUID(), UUID.randomUUID(), "Option", Instant.now(), Instant.now())),
                "{\"leftItems\":[{\"id\":\"l1\",\"label\":\"A\"}],\"rightItems\":[{\"id\":\"r1\",\"label\":\"1\"}],\"items\":[{\"id\":\"i1\",\"label\":\"Item\"}],\"blanks\":[{\"id\":\"b1\",\"placeholder\":\"Blank 1\"}],\"allowedFileTypes\":[\"application/pdf\"],\"unit\":\"kg\"}",
                Instant.now(),
                Instant.now()
        );
        return new TestStudentViewResponse(test, List.of(question), false, 100, 30, Instant.now());
    }

    private TestPreviewViewResponse previewViewResponse() {
        TestQuestionViewResponse question = new TestQuestionViewResponse(
                UUID.randomUUID(),
                testId,
                "Q",
                QuestionType.SINGLE_CHOICE,
                "desc",
                1,
                0,
                true,
                "feedback",
                "{\"correctValue\":42}",
                List.of(new TestQuestionAnswerResponse(UUID.randomUUID(), UUID.randomUUID(), "A", true, Instant.now(), Instant.now())),
                Instant.now(),
                Instant.now()
        );
        return new TestPreviewViewResponse(
                new TestResponse(
                        testId,
                        UUID.randomUUID(),
                        "Preview",
                        0,
                        TestStatus.PUBLISHED,
                        1,
                        100,
                        10,
                        null,
                        null,
                        true,
                        false,
                        false,
                        Instant.now(),
                        Instant.now()
                ),
                List.of(question),
                true,
                100,
                10,
                Instant.now()
        );
    }
}

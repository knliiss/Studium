package dev.knalis.education.controller;

import dev.knalis.education.exception.FileServiceUnavailableException;
import dev.knalis.education.service.lecture.LectureService;
import dev.knalis.shared.security.user.CurrentUserService;
import dev.knalis.shared.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LectureController.class)
@Import(GlobalExceptionHandler.class)
class LectureControllerErrorEnvelopeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LectureService lectureService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    void downloadFileServiceUnavailableErrorKeepsRequestIdEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String requestId = "lecture-file-unavailable";
        when(currentUserService.getCurrentUserId(any(Authentication.class))).thenReturn(userId);
        when(lectureService.downloadAttachment(eq(userId), anySet(), eq(lectureId), eq(attachmentId), anyBoolean()))
                .thenThrow(new FileServiceUnavailableException("metadata", fileId));

        mockMvc.perform(get("/api/v1/education/lectures/{lectureId}/attachments/{attachmentId}/download", lectureId, attachmentId)
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(userId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.errorCode").value("FILE_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/education/lectures/" + lectureId + "/attachments/" + attachmentId + "/download"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId) {
        return jwt().jwt(jwt -> jwt
                        .subject(userId.toString())
                        .claim("username", "student")
                        .tokenValue("test-token"))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}

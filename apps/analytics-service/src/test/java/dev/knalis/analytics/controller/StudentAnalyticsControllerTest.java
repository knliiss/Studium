package dev.knalis.analytics.controller;

import dev.knalis.analytics.dto.response.StudentAnalyticsResponse;
import dev.knalis.analytics.exception.AnalyticsAccessDeniedException;
import dev.knalis.analytics.service.AnalyticsAccessService;
import dev.knalis.analytics.service.AnalyticsReadService;
import dev.knalis.shared.security.user.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAnalyticsControllerTest {

    @Mock
    private AnalyticsReadService analyticsReadService;

    @Mock
    private AnalyticsAccessService analyticsAccessService;

    @Mock
    private CurrentUserService currentUserService;

    private StudentAnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentAnalyticsController(analyticsReadService, analyticsAccessService, currentUserService);
    }

    @Test
    void studentCannotAccessAnotherStudentAnalytics() {
        UUID currentUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Authentication authentication = auth("ROLE_STUDENT");
        when(currentUserService.getCurrentUserId(authentication)).thenReturn(currentUserId);

        assertThrows(
                AnalyticsAccessDeniedException.class,
                () -> controller.getStudentAnalytics(authentication, targetUserId)
        );
    }

    @Test
    void teacherCannotAccessUnrelatedStudentAnalytics() {
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Authentication authentication = auth("ROLE_TEACHER");
        when(currentUserService.getCurrentUserId(authentication)).thenReturn(teacherId);
        when(analyticsAccessService.canTeacherAccessStudent(teacherId, studentId)).thenReturn(false);

        assertThrows(
                AnalyticsAccessDeniedException.class,
                () -> controller.getStudentAnalytics(authentication, studentId)
        );
    }

    @Test
    void teacherCanAccessAssignedStudentAnalytics() {
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Authentication authentication = auth("ROLE_TEACHER");
        when(currentUserService.getCurrentUserId(authentication)).thenReturn(teacherId);
        when(analyticsAccessService.canTeacherAccessStudent(teacherId, studentId)).thenReturn(true);
        when(analyticsReadService.getStudentAnalytics(studentId)).thenReturn(emptyStudentAnalytics(studentId));

        controller.getStudentAnalytics(authentication, studentId);

        verify(analyticsReadService).getStudentAnalytics(studentId);
    }

    @Test
    void adminCanAccessAnyStudentAnalytics() {
        UUID currentUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Authentication authentication = auth("ROLE_ADMIN");
        when(currentUserService.getCurrentUserId(authentication)).thenReturn(currentUserId);
        when(analyticsReadService.getStudentAnalytics(targetUserId)).thenReturn(emptyStudentAnalytics(targetUserId));

        controller.getStudentAnalytics(authentication, targetUserId);

        verify(analyticsReadService).getStudentAnalytics(targetUserId);
    }

    private Authentication auth(String... roles) {
        return new UsernamePasswordAuthenticationToken(
                "user",
                "n/a",
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()
        );
    }

    private StudentAnalyticsResponse emptyStudentAnalytics(UUID userId) {
        return new StudentAnalyticsResponse(
                userId,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                0,
                0,
                null,
                null,
                null,
                List.of()
        );
    }
}

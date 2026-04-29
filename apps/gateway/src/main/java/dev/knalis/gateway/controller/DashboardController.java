package dev.knalis.gateway.controller;

import dev.knalis.gateway.dto.AdminDashboardOverviewResponse;
import dev.knalis.gateway.dto.StudentDashboardResponse;
import dev.knalis.gateway.dto.TeacherDashboardResponse;
import dev.knalis.gateway.filter.RequestIdFilter;
import dev.knalis.gateway.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/student/me")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public Mono<StudentDashboardResponse> getStudentDashboard(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange
    ) {
        return dashboardService.getStudentDashboard(
                UUID.fromString(authentication.getName()),
                authentication.getToken().getTokenValue(),
                requestId(exchange)
        );
    }

    @GetMapping("/teacher/me")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public Mono<TeacherDashboardResponse> getTeacherDashboard(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange
    ) {
        return dashboardService.getTeacherDashboard(
                UUID.fromString(authentication.getName()),
                authentication.getToken().getTokenValue(),
                requestId(exchange)
        );
    }

    @GetMapping("/admin/overview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Mono<AdminDashboardOverviewResponse> getAdminDashboard(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange
    ) {
        return dashboardService.getAdminDashboard(
                authentication.getToken().getTokenValue(),
                requestId(exchange)
        );
    }

    private String requestId(ServerWebExchange exchange) {
        String requestId = exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId == null || requestId.isBlank()) {
            requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        }
        return requestId == null ? "" : requestId;
    }
}

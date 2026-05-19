package dev.knalis.gateway.controller;

import dev.knalis.gateway.dto.gradebook.AdminGradebookOverviewResponse;
import dev.knalis.gateway.dto.gradebook.AdminGradebookResponse;
import dev.knalis.gateway.dto.gradebook.GradebookStudentSubjectResponse;
import dev.knalis.gateway.dto.gradebook.StudentGradebookResponse;
import dev.knalis.gateway.dto.gradebook.TeacherGradebookResponse;
import dev.knalis.gateway.filter.RequestIdFilter;
import dev.knalis.gateway.service.GradebookService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/gradebook")
public class GradebookController {

    private final GradebookService gradebookService;

    public GradebookController(GradebookService gradebookService) {
        this.gradebookService = gradebookService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public Mono<StudentGradebookResponse> getMyGradebook(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange
    ) {
        return gradebookService.getMyGradebook(
                UUID.fromString(authentication.getName()),
                authentication.getToken().getTokenValue(),
                requestId(exchange)
        );
    }

    @GetMapping("/me/subjects/{subjectId}")
    @PreAuthorize("hasRole('STUDENT')")
    public Mono<GradebookStudentSubjectResponse> getMySubjectGradebook(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @PathVariable UUID subjectId
    ) {
        return gradebookService.getMySubjectGradebook(
                UUID.fromString(authentication.getName()),
                subjectId,
                authentication.getToken().getTokenValue(),
                requestId(exchange)
        );
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public Mono<TeacherGradebookResponse> getTeacherGradebook(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String period
    ) {
        return gradebookService.getTeacherGradebook(
                UUID.fromString(authentication.getName()),
                authentication.getToken().getTokenValue(),
                requestId(exchange),
                subjectId,
                groupId,
                activityType
        );
    }

    @GetMapping("/teacher/subjects/{subjectId}/groups/{groupId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public Mono<TeacherGradebookResponse> getTeacherGradebookDetail(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @PathVariable UUID subjectId,
            @PathVariable UUID groupId,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String period
    ) {
        return gradebookService.getTeacherGradebookDetail(
                UUID.fromString(authentication.getName()),
                subjectId,
                groupId,
                authentication.getToken().getTokenValue(),
                requestId(exchange),
                activityType
        );
    }

    @GetMapping("/admin/overview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Mono<AdminGradebookOverviewResponse> getAdminOverview(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String period
    ) {
        return gradebookService.getAdminOverview(
                authentication.getToken().getTokenValue(),
                requestId(exchange),
                teacherId
        );
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Mono<AdminGradebookResponse> getAdminGradebook(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String period
    ) {
        return gradebookService.getAdminGradebook(
                authentication.getToken().getTokenValue(),
                requestId(exchange),
                subjectId,
                groupId,
                teacherId,
                activityType
        );
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public Mono<ResponseEntity<byte[]>> export(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String teacherId,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String period
    ) {
        return gradebookService.exportGradebook(
                UUID.fromString(authentication.getName()),
                authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).collect(Collectors.toSet()),
                authentication.getToken().getTokenValue(),
                requestId(exchange),
                normalizeUuidFilter(subjectId, "subjectId"),
                normalizeUuidFilter(groupId, "groupId"),
                normalizeUuidFilter(teacherId, "teacherId"),
                normalizeTextFilter(activityType)
        );
    }

    private UUID normalizeUuidFilter(String rawValue, String parameterName) {
        String normalized = normalizeTextFilter(rawValue);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid value for parameter '" + parameterName + "'");
        }
    }

    private String normalizeTextFilter(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim();
        if (normalized.isEmpty()
                || "null".equalsIgnoreCase(normalized)
                || "undefined".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String requestId(ServerWebExchange exchange) {
        String requestId = exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId == null || requestId.isBlank()) {
            requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        }
        return requestId == null ? "" : requestId;
    }
}

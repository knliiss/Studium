package dev.knalis.gateway.controller;

import dev.knalis.gateway.dto.ResolvedLessonResponse;
import dev.knalis.gateway.filter.RequestIdFilter;
import dev.knalis.gateway.service.MyScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/schedule/me")
@RequiredArgsConstructor
public class MyScheduleController {
    
    private final MyScheduleService myScheduleService;
    
    @GetMapping("/week")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public Mono<List<ResolvedLessonResponse>> getMyWeek(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @RequestParam LocalDate startDate
    ) {
        return myScheduleService.getMyWeek(
                UUID.fromString(authentication.getName()),
                authentication.getToken().getTokenValue(),
                requestId(exchange),
                startDate,
                currentRoles(authentication)
        );
    }
    
    @GetMapping("/range")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public Mono<List<ResolvedLessonResponse>> getMyRange(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo
    ) {
        return myScheduleService.getMyRange(
                UUID.fromString(authentication.getName()),
                authentication.getToken().getTokenValue(),
                requestId(exchange),
                dateFrom,
                dateTo,
                currentRoles(authentication)
        );
    }

    @GetMapping(value = "/export.ics", produces = "text/calendar")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public Mono<ResponseEntity<String>> exportMyCalendar(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo
    ) {
        return myScheduleService.exportMyCalendar(
                        UUID.fromString(authentication.getName()),
                        authentication.getToken().getTokenValue(),
                        requestId(exchange),
                        dateFrom,
                        dateTo,
                        currentRoles(authentication)
                )
                .map(calendar -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/calendar"))
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename("my-schedule.ics").build().toString()
                        )
                        .body(calendar));
    }
    
    private String requestId(ServerWebExchange exchange) {
        String requestId = exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId == null || requestId.isBlank()) {
            requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        }
        return requestId == null ? "" : requestId;
    }

    private Set<String> currentRoles(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .collect(Collectors.toSet());
    }
}

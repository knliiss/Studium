package dev.knalis.gateway.controller;

import dev.knalis.gateway.filter.RequestIdFilter;
import dev.knalis.gateway.service.GradebookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradebookControllerTest {

    @Mock
    private GradebookService gradebookService;

    private GradebookController gradebookController;

    @BeforeEach
    void setUp() {
        gradebookController = new GradebookController(gradebookService);
    }

    @Test
    void exportNormalizesBlankNullAndUndefinedParamsToNull() {
        JwtAuthenticationToken authentication = authenticationToken();
        ServerWebExchange exchange = exchange();

        when(gradebookService.exportGradebook(
                eq(UUID.fromString(authentication.getName())),
                eq(Set.of("ROLE_OWNER")),
                eq("token"),
                eq("request-id"),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        )).thenReturn(Mono.just(ResponseEntity.ok(new byte[0])));

        ResponseEntity<byte[]> response = gradebookController.export(
                authentication,
                exchange,
                "",
                "null",
                "undefined",
                " ",
                ""
        ).block();

        assertEquals(200, response.getStatusCode().value());
        verify(gradebookService).exportGradebook(
                eq(UUID.fromString(authentication.getName())),
                eq(Set.of("ROLE_OWNER")),
                eq("token"),
                eq("request-id"),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    void exportParsesValidUuidsAndKeepsActivityType() {
        JwtAuthenticationToken authentication = authenticationToken();
        ServerWebExchange exchange = exchange();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();

        when(gradebookService.exportGradebook(
                eq(UUID.fromString(authentication.getName())),
                eq(Set.of("ROLE_OWNER")),
                eq("token"),
                eq("request-id"),
                eq(subjectId),
                eq(groupId),
                eq(teacherId),
                eq("ALL")
        )).thenReturn(Mono.just(ResponseEntity.ok(new byte[0])));

        ResponseEntity<byte[]> response = gradebookController.export(
                authentication,
                exchange,
                subjectId.toString(),
                groupId.toString(),
                teacherId.toString(),
                "ALL",
                null
        ).block();

        assertEquals(200, response.getStatusCode().value());
        verify(gradebookService).exportGradebook(
                eq(UUID.fromString(authentication.getName())),
                eq(Set.of("ROLE_OWNER")),
                eq("token"),
                eq("request-id"),
                eq(subjectId),
                eq(groupId),
                eq(teacherId),
                eq("ALL")
        );
    }

    @Test
    void exportRejectsInvalidUuidFilter() {
        JwtAuthenticationToken authentication = authenticationToken();
        ServerWebExchange exchange = exchange();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> gradebookController.export(authentication, exchange, "not-a-uuid", null, null, "ALL", null)
        );

        assertEquals("Invalid value for parameter 'subjectId'", exception.getMessage());
    }

    private JwtAuthenticationToken authenticationToken() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("roles", List.of("OWNER"))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    }

    private ServerWebExchange exchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/gradebook/export").build());
        exchange.getAttributes().put(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-id");
        return exchange;
    }
}

package dev.knalis.gateway.controller;

import dev.knalis.gateway.dto.SearchPageResponse;
import dev.knalis.gateway.filter.RequestIdFilter;
import dev.knalis.gateway.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final GlobalSearchService globalSearchService;

    @GetMapping
    // TODO: Expand search access once assignment/test ownership scoping is implemented safely.
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Mono<SearchPageResponse> search(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "title") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return globalSearchService.search(
                authentication.getToken().getTokenValue(),
                requestId(exchange),
                q,
                page,
                size,
                sortBy,
                direction
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

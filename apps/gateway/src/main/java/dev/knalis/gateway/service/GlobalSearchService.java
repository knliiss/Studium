package dev.knalis.gateway.service;

import dev.knalis.gateway.client.assignment.AssignmentServiceClient;
import dev.knalis.gateway.client.education.EducationServiceClient;
import dev.knalis.gateway.client.testing.TestingServiceClient;
import dev.knalis.gateway.dto.SearchItemResponse;
import dev.knalis.gateway.dto.SearchPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "title",
            "type",
            "sourceService"
    );

    private final EducationServiceClient educationServiceClient;
    private final AssignmentServiceClient assignmentServiceClient;
    private final TestingServiceClient testingServiceClient;

    public Mono<SearchPageResponse> search(
            String bearerToken,
            String requestId,
            String query,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        int requestedWindowSize = Math.min(Math.max((Math.max(page, 0) + 1) * Math.max(size, 1), 1), 100);
        return Mono.zip(
                        educationServiceClient.search(bearerToken, requestId, query, 0, requestedWindowSize),
                        assignmentServiceClient.search(bearerToken, requestId, query, 0, requestedWindowSize),
                        testingServiceClient.search(bearerToken, requestId, query, 0, requestedWindowSize)
                )
                .map(tuple -> {
                    List<SearchItemResponse> merged = Stream.concat(
                                    Stream.concat(
                                            tuple.getT1().items().stream().map(item -> new SearchItemResponse(
                                                    item.type(),
                                                    item.id(),
                                                    item.title(),
                                                    item.subtitle(),
                                                    "education-service",
                                                    item.targetMetadata()
                                            )),
                                            tuple.getT2().items().stream().map(item -> new SearchItemResponse(
                                                    item.type(),
                                                    item.id(),
                                                    item.title(),
                                                    item.subtitle(),
                                                    "assignment-service",
                                                    item.targetMetadata()
                                            ))
                                    ),
                                    tuple.getT3().items().stream().map(item -> new SearchItemResponse(
                                            item.type(),
                                            item.id(),
                                            item.title(),
                                            item.subtitle(),
                                            "testing-service",
                                            item.targetMetadata()
                                    ))
                            )
                            .sorted(comparator(sortBy, direction))
                            .toList();

                    int safePage = Math.max(page, 0);
                    int safeSize = Math.min(Math.max(size, 1), 100);
                    int fromIndex = Math.min(safePage * safeSize, merged.size());
                    int toIndex = Math.min(fromIndex + safeSize, merged.size());
                    long totalElements = tuple.getT1().totalElements() + tuple.getT2().totalElements() + tuple.getT3().totalElements();
                    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);

                    return new SearchPageResponse(
                            merged.subList(fromIndex, toIndex),
                            safePage,
                            safeSize,
                            totalElements,
                            totalPages,
                            safePage == 0,
                            totalPages == 0 || safePage >= totalPages - 1
                    );
                });
    }

    private Comparator<SearchItemResponse> comparator(String sortBy, String direction) {
        Comparator<SearchItemResponse> comparator;
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "title";
        if ("type".equals(safeSortBy)) {
            comparator = Comparator.comparing(SearchItemResponse::type, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(SearchItemResponse::title, String.CASE_INSENSITIVE_ORDER);
        } else if ("sourceService".equals(safeSortBy)) {
            comparator = Comparator.comparing(SearchItemResponse::sourceService, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(SearchItemResponse::title, String.CASE_INSENSITIVE_ORDER);
        } else {
            comparator = Comparator.comparing(SearchItemResponse::title, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(item -> item.id().toString());
        }
        return "desc".equalsIgnoreCase(direction) ? comparator.reversed() : comparator;
    }
}

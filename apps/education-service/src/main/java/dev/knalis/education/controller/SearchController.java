package dev.knalis.education.controller;

import dev.knalis.education.dto.response.SearchPageResponse;
import dev.knalis.education.service.search.EducationSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/education/search")
@RequiredArgsConstructor
public class SearchController {

    private final EducationSearchService educationSearchService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SearchPageResponse search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return educationSearchService.search(q, page, size);
    }
}

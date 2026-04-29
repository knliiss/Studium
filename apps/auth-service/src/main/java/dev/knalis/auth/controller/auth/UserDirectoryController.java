package dev.knalis.auth.controller.auth;

import dev.knalis.auth.dto.request.UserDirectoryLookupRequest;
import dev.knalis.auth.dto.response.UserSummaryResponse;
import dev.knalis.auth.service.auth.UserDirectoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserDirectoryController {

    private final UserDirectoryService userDirectoryService;

    @PostMapping("/lookup")
    @PreAuthorize("isAuthenticated()")
    public List<UserSummaryResponse> lookupUsers(@Valid @RequestBody UserDirectoryLookupRequest request) {
        return userDirectoryService.lookupUsers(request.userIds());
    }
}

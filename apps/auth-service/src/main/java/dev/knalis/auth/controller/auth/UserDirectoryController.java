package dev.knalis.auth.controller.auth;

import dev.knalis.auth.dto.request.UserDirectoryLookupRequest;
import dev.knalis.auth.dto.response.UserSummaryResponse;
import dev.knalis.auth.service.auth.UserDirectoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserDirectoryController {

    private final UserDirectoryService userDirectoryService;

    @PostMapping("/lookup")
    @PreAuthorize("isAuthenticated()")
    public List<UserSummaryResponse> lookupUsers(
            Authentication authentication,
            @Valid @RequestBody UserDirectoryLookupRequest request
    ) {
        return userDirectoryService.lookupUsers(request.userIds(), canAccessPrivateDirectoryFields(authentication));
    }

    private boolean canAccessPrivateDirectoryFields(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return roles.contains("ROLE_OWNER") || roles.contains("ROLE_ADMIN");
    }
}

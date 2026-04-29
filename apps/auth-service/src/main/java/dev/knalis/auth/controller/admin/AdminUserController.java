package dev.knalis.auth.controller.admin;

import dev.knalis.auth.dto.request.AdminBanUserRequest;
import dev.knalis.auth.dto.request.AdminUpdateUserRolesRequest;
import dev.knalis.auth.dto.response.AdminUserPageResponse;
import dev.knalis.auth.dto.response.AdminUserResponse;
import dev.knalis.auth.dto.response.AdminUserStatsResponse;
import dev.knalis.auth.entity.Role;
import dev.knalis.auth.service.admin.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AdminUserController {
    
    private final UserAdminService userAdminService;
    
    @GetMapping("/statistics")
    public AdminUserStatsResponse getStatistics() {
        return userAdminService.getStats();
    }
    
    @GetMapping
    public AdminUserPageResponse getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean banned
    ) {
        return userAdminService.getUsers(
                page,
                size,
                sortBy,
                direction,
                search,
                role,
                banned
        );
    }
    
    @GetMapping("/{userId:[0-9a-fA-F\\-]{36}}")
    public AdminUserResponse getUser(@PathVariable UUID userId) {
        return userAdminService.getUser(userId);
    }
    
    @PatchMapping("/{userId:[0-9a-fA-F\\-]{36}}/roles")
    public AdminUserResponse updateRoles(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUpdateUserRolesRequest request
    ) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        UUID actorId = UUID.fromString(jwt.getSubject());
        Set<Role> actorRoles = Set.copyOf(
                jwt.getClaimAsStringList("roles").stream()
                        .map(Role::valueOf)
                        .toList()
        );
        
        return userAdminService.updateRoles(actorId, actorRoles, userId, request.roles());
    }
    
    @PostMapping("/{userId:[0-9a-fA-F\\-]{36}}/ban")
    public void banUser(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminBanUserRequest request
    ) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        UUID actorId = UUID.fromString(jwt.getSubject());
        Set<Role> actorRoles = Set.copyOf(
                jwt.getClaimAsStringList("roles").stream()
                        .map(Role::valueOf)
                        .toList()
        );
        
        userAdminService.banUser(actorId, actorRoles, userId, request.reason(), request.expiresAt());
    }
    
    @PostMapping("/{userId:[0-9a-fA-F\\-]{36}}/unban")
    public void unbanUser(
            Authentication authentication,
            @PathVariable UUID userId
    ) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        UUID actorId = UUID.fromString(jwt.getSubject());
        Set<Role> actorRoles = Set.copyOf(
                jwt.getClaimAsStringList("roles").stream()
                        .map(Role::valueOf)
                        .toList()
        );
        
        userAdminService.unbanUser(actorId, actorRoles, userId);
    }
    
    @PostMapping("/{userId:[0-9a-fA-F\\-]{36}}/revoke-sessions")
    public void revokeAllSessions(
            Authentication authentication,
            @PathVariable UUID userId
    ) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        UUID actorId = UUID.fromString(jwt.getSubject());
        Set<Role> actorRoles = Set.copyOf(
                jwt.getClaimAsStringList("roles").stream()
                        .map(Role::valueOf)
                        .toList()
        );
        
        userAdminService.revokeAllSessions(actorId, actorRoles, userId);
    }
}

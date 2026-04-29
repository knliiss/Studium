package dev.knalis.auth.service.admin;

import dev.knalis.auth.dto.response.AdminUserPageResponse;
import dev.knalis.auth.dto.response.AdminUserResponse;
import dev.knalis.auth.dto.response.AdminUserStatsResponse;
import dev.knalis.auth.entity.Role;
import dev.knalis.auth.entity.User;
import dev.knalis.contracts.event.UserBannedEvent;
import dev.knalis.auth.exception.AccessHierarchyViolationException;
import dev.knalis.auth.exception.UserNotFoundException;
import dev.knalis.auth.factory.ban.UserBanFactory;
import dev.knalis.auth.mapper.admin.AdminUserMapper;
import dev.knalis.auth.repository.UserBanRepository;
import dev.knalis.auth.repository.UserRepository;
import dev.knalis.auth.repository.spec.UserSpecifications;
import dev.knalis.auth.service.common.AuthAuditService;
import dev.knalis.auth.service.ban.UserBanService;
import dev.knalis.auth.service.common.AuthEventPublisher;
import dev.knalis.auth.service.common.RoleHierarchyService;
import dev.knalis.auth.service.token.TokenService;
import dev.knalis.contracts.event.UserUnbannedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdminService {
    
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "username",
            "email"
    );
    
    private final UserRepository userRepository;
    private final UserBanRepository userBanRepository;
    private final UserBanService userBanService;
    private final TokenService tokenService;
    private final RoleHierarchyService roleHierarchyService;
    private final UserBanFactory userBanFactory;
    private final AdminUserMapper adminUserMapper;
    private final AuthAuditService authAuditService;
    private final AuthEventPublisher authEventPublisher;
    
    @Transactional(readOnly = true)
    public AdminUserStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long totalBannedUsers = userBanRepository.countDistinctActiveBannedUsers();
        long totalEnabledUsers = Math.max(totalUsers - totalBannedUsers, 0);
        long totalOwners = userRepository.countByRolesContaining(Role.OWNER);
        long totalAdmins = userRepository.countByRolesContaining(Role.ADMIN);
        long totalTeachers = userRepository.countByRolesContaining(Role.TEACHER);
        long totalStudents = userRepository.countByRolesContaining(Role.STUDENT);
        long totalRegularUsers = userRepository.countByRolesContaining(Role.USER);
        
        return new AdminUserStatsResponse(
                totalUsers,
                totalEnabledUsers,
                totalBannedUsers,
                totalOwners,
                totalAdmins,
                totalTeachers,
                totalStudents,
                totalRegularUsers
        );
    }
    
    @Transactional(readOnly = true)
    public AdminUserPageResponse getUsers(
            int page,
            int size,
            String sortBy,
            String direction,
            String search,
            Role role,
            Boolean banned
    ) {
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(sortDirection, safeSortBy)
        );
        
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and(UserSpecifications.usernameOrEmailContains(search));
        spec = spec.and(UserSpecifications.hasRole(role));
        spec = spec.and(UserSpecifications.bannedEquals(banned));
        
        Page<User> userPage = userRepository.findAll(spec, pageable);
        
        return new AdminUserPageResponse(
                userPage.getContent().stream()
                        .map(adminUserMapper::toResponse)
                        .toList(),
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isFirst(),
                userPage.isLast()
        );
    }
    
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        return adminUserMapper.toResponse(user);
    }
    
    @Transactional
    public AdminUserResponse updateRoles(UUID actorId, Set<Role> actorRoles, UUID targetUserId, Set<Role> newRoles) {
        User target = userRepository.findById(targetUserId).orElseThrow(UserNotFoundException::new);
        AdminUserResponse oldValue = adminUserMapper.toResponse(target);
        
        if (actorId.equals(targetUserId) && actorRoles.contains(Role.OWNER) && !newRoles.contains(Role.OWNER)) {
            throw new AccessHierarchyViolationException("Owner cannot remove own OWNER role");
        }
        
        roleHierarchyService.requireCanManage(actorRoles, target.getRoles());
        roleHierarchyService.requireCanAssignRoles(actorRoles, newRoles);
        target.setRoles(newRoles);
        
        AdminUserResponse response = adminUserMapper.toResponse(userRepository.save(target));
        authAuditService.record(actorId, "USER_ROLES_CHANGED", "USER", response.id(), oldValue, response);
        return response;
    }
    
    @Transactional
    public void banUser(UUID actorId, Set<Role> actorRoles, UUID targetUserId, String reason, Instant expiresAt) {
        User target = userRepository.findById(targetUserId).orElseThrow(UserNotFoundException::new);
        User actor = userRepository.findById(actorId).orElseThrow(UserNotFoundException::new);
        AdminUserResponse oldValue = adminUserMapper.toResponse(target);
        
        roleHierarchyService.requireCanManage(actorRoles, target.getRoles());
        
        var ban = userBanFactory.newBan(targetUserId, actorId, reason, expiresAt);
        userBanService.save(ban);
        tokenService.revokeAllRefreshTokens(targetUserId);
        
        authEventPublisher.publishUserBanned(
                new UserBannedEvent(
                        UUID.randomUUID(),
                        target.getId(),
                        target.getEmail(),
                        target.getUsername(),
                        reason,
                        expiresAt,
                        actor.getId(),
                        actor.getUsername(),
                        Instant.now()
                )
        );
        authAuditService.record(actorId, "USER_BANNED", "USER", target.getId(), oldValue, adminUserMapper.toResponse(target));
    }
    
    @Transactional
    public void unbanUser(UUID actorId, Set<Role> actorRoles, UUID targetUserId) {
        User target = userRepository.findById(targetUserId).orElseThrow(UserNotFoundException::new);
        User actor = userRepository.findById(actorId).orElseThrow(UserNotFoundException::new);
        AdminUserResponse oldValue = adminUserMapper.toResponse(target);
        
        roleHierarchyService.requireCanManage(actorRoles, target.getRoles());
        
        userBanService.findActiveBan(targetUserId).ifPresent(ban -> {
            ban.setActive(false);
            userBanService.save(ban);
        });
        
        authEventPublisher.publishUserUnbanned(
                new UserUnbannedEvent(
                        UUID.randomUUID(),
                        target.getId(),
                        target.getEmail(),
                        target.getUsername(),
                        actor.getId(),
                        actor.getUsername(),
                        Instant.now()
                )
        );
        authAuditService.record(actorId, "USER_UNBANNED", "USER", target.getId(), oldValue, adminUserMapper.toResponse(target));
    }
    
    @Transactional
    public void revokeAllSessions(UUID actorId, Set<Role> actorRoles, UUID targetUserId) {
        User target = userRepository.findById(targetUserId).orElseThrow(UserNotFoundException::new);
        roleHierarchyService.requireCanManage(actorRoles, target.getRoles());
        
        tokenService.revokeAllRefreshTokens(targetUserId);
    }
}

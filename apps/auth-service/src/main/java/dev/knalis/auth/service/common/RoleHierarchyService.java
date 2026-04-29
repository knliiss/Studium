package dev.knalis.auth.service.common;

import dev.knalis.auth.entity.Role;
import dev.knalis.auth.exception.AccessHierarchyViolationException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RoleHierarchyService {
    
    public boolean canManage(Set<Role> actorRoles, Set<Role> targetRoles) {
        return rank(actorRoles) > rank(targetRoles);
    }
    
    public void requireCanManage(Set<Role> actorRoles, Set<Role> targetRoles) {
        if (!canManage(actorRoles, targetRoles)) {
            throw new AccessHierarchyViolationException("Insufficient role hierarchy to manage target user");
        }
    }

    public void requireCanAssignRoles(Set<Role> actorRoles, Set<Role> assignedRoles) {
        if (assignedRoles.contains(Role.OWNER)) {
            throw new AccessHierarchyViolationException("OWNER role cannot be assigned through user role management");
        }
        if (assignedRoles.contains(Role.ADMIN) && !actorRoles.contains(Role.OWNER)) {
            throw new AccessHierarchyViolationException("Only owner can assign ADMIN role");
        }
    }
    
    private int rank(Set<Role> roles) {
        if (roles.contains(Role.OWNER)) {
            return 3;
        }
        if (roles.contains(Role.ADMIN)) {
            return 2;
        }
        return 1;
    }
}

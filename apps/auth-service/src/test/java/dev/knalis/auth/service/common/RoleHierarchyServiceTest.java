package dev.knalis.auth.service.common;

import dev.knalis.auth.entity.Role;
import dev.knalis.auth.exception.AccessHierarchyViolationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleHierarchyServiceTest {

    private final RoleHierarchyService roleHierarchyService = new RoleHierarchyService();

    @Test
    void rejectsOwnerAssignmentForEveryActor() {
        assertThrows(
                AccessHierarchyViolationException.class,
                () -> roleHierarchyService.requireCanAssignRoles(Set.of(Role.OWNER), Set.of(Role.OWNER))
        );
    }

    @Test
    void rejectsAdminAssigningAdmin() {
        assertThrows(
                AccessHierarchyViolationException.class,
                () -> roleHierarchyService.requireCanAssignRoles(Set.of(Role.ADMIN), Set.of(Role.ADMIN))
        );
    }

    @Test
    void allowsOwnerAssigningAdminAndAcademicRoles() {
        assertDoesNotThrow(
                () -> roleHierarchyService.requireCanAssignRoles(
                        Set.of(Role.OWNER),
                        Set.of(Role.ADMIN, Role.TEACHER, Role.STUDENT, Role.USER)
                )
        );
    }

    @Test
    void allowsAdminAssigningTeacherStudentAndUser() {
        assertDoesNotThrow(
                () -> roleHierarchyService.requireCanAssignRoles(
                        Set.of(Role.ADMIN),
                        Set.of(Role.TEACHER, Role.STUDENT, Role.USER)
                )
        );
    }
}

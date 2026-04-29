package dev.knalis.auth.repository.spec;

import dev.knalis.auth.entity.Role;
import dev.knalis.auth.entity.User;
import dev.knalis.auth.entity.UserBan;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class UserSpecifications {
    
    private UserSpecifications() {
    }
    
    public static Specification<User> usernameOrEmailContains(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)
            );
        };
    }
    
    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> {
            if (role == null) {
                return cb.conjunction();
            }
            
            query.distinct(true);
            var rolesJoin = root.join("roles");
            return cb.equal(rolesJoin, role);
        };
    }
    
    public static Specification<User> enabledEquals(Boolean enabled) {
        return (root, query, cb) -> enabled == null
                ? cb.conjunction()
                : cb.equal(root.get("enabled"), enabled);
    }
    
    public static Specification<User> lockedEquals(Boolean locked) {
        return (root, query, cb) -> locked == null
                ? cb.conjunction()
                : cb.equal(root.get("locked"), locked);
    }
    
    public static Specification<User> bannedEquals(Boolean banned) {
        return (root, query, cb) -> {
            if (banned == null) {
                return cb.conjunction();
            }
            
            var subquery = query.subquery(UserBan.class);
            var banRoot = subquery.from(UserBan.class);
            
            var activeBanPredicate = cb.and(
                    cb.equal(banRoot.get("userId"), root.get("id")),
                    cb.isTrue(banRoot.get("active")),
                    cb.or(
                            cb.isNull(banRoot.get("expiresAt")),
                            cb.greaterThan(banRoot.get("expiresAt"), Instant.now())
                    )
            );
            
            subquery.select(banRoot).where(activeBanPredicate);
            
            return banned ? cb.exists(subquery) : cb.not(cb.exists(subquery));
        };
    }
}
package dev.knalis.auth.repository;

import dev.knalis.auth.entity.UserBan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBanRepository extends JpaRepository<UserBan, UUID> {
    
    List<UserBan> findAllByUserIdAndActiveTrue(UUID userId);
    
    Optional<UserBan> findFirstByUserIdAndActiveTrueAndExpiresAtIsNull(UUID userId);
    
    Optional<UserBan> findFirstByUserIdAndActiveTrueAndExpiresAtAfter(UUID userId, Instant now);
    
    @Query("""
            select count(distinct ub.userId)
            from UserBan ub
            where ub.active = true
              and (ub.expiresAt is null or ub.expiresAt > CURRENT_TIMESTAMP)
            """)
    long countDistinctActiveBannedUsers();
}
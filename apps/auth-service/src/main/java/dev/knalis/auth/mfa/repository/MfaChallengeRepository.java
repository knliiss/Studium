package dev.knalis.auth.mfa.repository;

import dev.knalis.auth.mfa.entity.MfaChallenge;
import dev.knalis.auth.mfa.entity.MfaChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {
    
    Optional<MfaChallenge> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update MfaChallenge challenge
               set challenge.status = :cancelledStatus,
                   challenge.completedAt = :now,
                   challenge.updatedAt = :now
             where challenge.userId = :userId
               and challenge.status in :pendingStatuses
               and challenge.expiresAt >= :now
            """)
    int cancelPendingForUser(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("cancelledStatus") MfaChallengeStatus cancelledStatus,
            @Param("pendingStatuses") Collection<MfaChallengeStatus> pendingStatuses
    );

    default int cancelPendingForUser(UUID userId, Instant now) {
        return cancelPendingForUser(
                userId,
                now,
                MfaChallengeStatus.CANCELLED,
                List.of(MfaChallengeStatus.PENDING_SELECTION, MfaChallengeStatus.PENDING_VERIFICATION)
        );
    }
}

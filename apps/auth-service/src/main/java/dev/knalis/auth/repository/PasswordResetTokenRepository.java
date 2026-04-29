package dev.knalis.auth.repository;

import dev.knalis.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    
    List<PasswordResetToken> findAllByUserId(UUID userId);

    @Modifying
    @Query("""
            update PasswordResetToken token
               set token.revoked = true,
                   token.updatedAt = :now
             where token.userId = :userId
               and token.revoked = false
               and token.used = false
            """)
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}

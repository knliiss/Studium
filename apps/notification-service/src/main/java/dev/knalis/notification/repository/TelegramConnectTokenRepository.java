package dev.knalis.notification.repository;

import dev.knalis.notification.entity.TelegramConnectToken;
import dev.knalis.notification.entity.TelegramConnectTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TelegramConnectTokenRepository extends JpaRepository<TelegramConnectToken, UUID> {

    Optional<TelegramConnectToken> findByTokenHash(String tokenHash);

    Optional<TelegramConnectToken> findFirstByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
            UUID userId,
            TelegramConnectTokenStatus status,
            Instant expiresAt
    );

    @Modifying
    @Query("""
            update TelegramConnectToken token
            set token.status = :newStatus,
                token.revokedAt = :changedAt
            where token.userId = :userId
              and token.status = :currentStatus
            """)
    int updateStatusByUserIdAndStatus(
            @Param("userId") UUID userId,
            @Param("currentStatus") TelegramConnectTokenStatus currentStatus,
            @Param("newStatus") TelegramConnectTokenStatus newStatus,
            @Param("changedAt") Instant changedAt
    );

    @Modifying
    @Query("""
            update TelegramConnectToken token
            set token.status = :newStatus,
                token.revokedAt = :changedAt
            where token.status = :currentStatus
              and token.expiresAt < :now
            """)
    int expirePendingTokens(
            @Param("currentStatus") TelegramConnectTokenStatus currentStatus,
            @Param("newStatus") TelegramConnectTokenStatus newStatus,
            @Param("changedAt") Instant changedAt,
            @Param("now") Instant now
    );
}

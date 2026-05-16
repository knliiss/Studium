package dev.knalis.notification.repository;

import dev.knalis.notification.entity.TelegramLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TelegramLinkRepository extends JpaRepository<TelegramLink, UUID> {

    Optional<TelegramLink> findByUserIdAndActiveTrue(UUID userId);

    Optional<TelegramLink> findByTelegramUserIdAndActiveTrue(Long telegramUserId);

    Optional<TelegramLink> findByChatIdAndActiveTrue(Long chatId);

    Page<TelegramLink> findAllByOrderByConnectedAtDesc(Pageable pageable);

    long countByActiveTrue();

    long countByActiveFalse();

    @Query("select coalesce(sum(link.deliveryFailureCount), 0) from TelegramLink link")
    long totalDeliveryFailureCount();

    @Query("select coalesce(sum(link.telegramSentCount), 0) from TelegramLink link")
    long totalTelegramSentCount();
}

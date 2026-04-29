package dev.knalis.auth.service.ban;

import dev.knalis.auth.entity.UserBan;
import dev.knalis.auth.repository.UserBanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

import static dev.knalis.auth.config.CacheConfig.ACTIVE_BANS_CACHE;

@Component
@RequiredArgsConstructor
public class ActiveBanCacheService {
    
    private final UserBanRepository userBanRepository;
    
    @Cacheable(cacheNames = ACTIVE_BANS_CACHE, key = "#userId", unless = "#result == null")
    public UserBan getActiveBan(UUID userId) {
        return userBanRepository.findFirstByUserIdAndActiveTrueAndExpiresAtIsNull(userId)
                .or(() -> userBanRepository.findFirstByUserIdAndActiveTrueAndExpiresAtAfter(userId, Instant.now()))
                .orElse(null);
    }
    
    @CacheEvict(cacheNames = ACTIVE_BANS_CACHE, key = "#userId")
    public void evict(UUID userId) {
    }
}

package dev.knalis.auth.service.ban;

import dev.knalis.auth.entity.UserBan;
import dev.knalis.auth.repository.UserBanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserBanService {
    
    private final UserBanRepository userBanRepository;
    private final ActiveBanCacheService activeBanCacheService;
    
    @Transactional(readOnly = true)
    public Optional<UserBan> findActiveBan(UUID userId) {
        return Optional.ofNullable(activeBanCacheService.getActiveBan(userId));
    }
    
    @Transactional
    public UserBan save(UserBan userBan) {
        UserBan saved = userBanRepository.save(userBan);
        activeBanCacheService.evict(saved.getUserId());
        return saved;
    }
}

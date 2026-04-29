package dev.knalis.auth.factory.ban;

import dev.knalis.auth.entity.UserBan;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class UserBanFactory {
    
    public UserBan newBan(UUID targetUserId, UUID actorId, String reason, Instant expiresAt) {
        UserBan ban = new UserBan();
        ban.setUserId(targetUserId);
        ban.setCreatedBy(actorId);
        ban.setReason(reason);
        ban.setExpiresAt(expiresAt);
        ban.setActive(true);
        return ban;
    }
}
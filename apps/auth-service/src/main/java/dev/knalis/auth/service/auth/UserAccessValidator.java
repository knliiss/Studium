package dev.knalis.auth.service.auth;

import dev.knalis.auth.entity.User;
import dev.knalis.auth.entity.UserBan;
import dev.knalis.auth.exception.UserBannedException;
import dev.knalis.auth.service.ban.UserBanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UserAccessValidator {

    private final UserBanService userBanService;
    private final Clock clock;

    public void validate(User user) {
        UserBan activeBan = userBanService.findActiveBan(user.getId()).orElse(null);
        if (activeBan == null) {
            return;
        }
        if (activeBan.getExpiresAt() == null) {
            throw new UserBannedException();
        }
        if (activeBan.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new UserBannedException(activeBan.getExpiresAt());
        }
    }
}

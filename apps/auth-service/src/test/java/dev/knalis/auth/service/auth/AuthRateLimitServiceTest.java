package dev.knalis.auth.service.auth;

import dev.knalis.auth.config.AuthProperties;
import dev.knalis.auth.exception.AuthRateLimitExceededException;
import dev.knalis.auth.service.common.TokenHashService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRateLimitServiceTest {

    private MutableClock clock;
    private AuthRateLimitService authRateLimitService;
    private Map<String, WindowValue> redisWindows;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.getRateLimit().getLoginIp().setMaxRequests(2);
        properties.getRateLimit().getLoginIp().setWindow(Duration.ofSeconds(30));
        properties.getRateLimit().getLoginUsername().setMaxRequests(10);
        properties.getRateLimit().getLoginUsername().setWindow(Duration.ofSeconds(30));

        clock = new MutableClock(Instant.parse("2026-04-17T10:15:30Z"));
        redisWindows = new HashMap<>();

        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.execute(any(), anyList(), any())).thenAnswer(invocation -> {
            List<String> keys = invocation.getArgument(1);
            String key = keys.getFirst();
            long ttlMillis = Long.parseLong(invocation.getArgument(2).toString());
            Instant expiresAt = clock.instant().plusMillis(ttlMillis);

            WindowValue current = redisWindows.get(key);
            if (current == null || !current.expiresAt().isAfter(clock.instant())) {
                current = new WindowValue(0L, expiresAt);
            }

            WindowValue updated = new WindowValue(current.count() + 1, expiresAt);
            redisWindows.put(key, updated);

            long remainingTtl = Math.max(1L, Duration.between(clock.instant(), updated.expiresAt()).toMillis());
            return List.of(updated.count(), remainingTtl);
        });

        authRateLimitService = new AuthRateLimitService(
                properties,
                stringRedisTemplate,
                new TokenHashService(),
                clock,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void loginLimitAllowsRequestsWithinWindow() {
        assertDoesNotThrow(() -> authRateLimitService.ensureLoginAllowed("127.0.0.1", "user"));
        assertDoesNotThrow(() -> authRateLimitService.ensureLoginAllowed("127.0.0.1", "user"));
    }

    @Test
    void loginLimitRejectsRequestOverLimit() {
        authRateLimitService.ensureLoginAllowed("127.0.0.1", "user");
        authRateLimitService.ensureLoginAllowed("127.0.0.1", "user");

        assertThrows(AuthRateLimitExceededException.class, () ->
                authRateLimitService.ensureLoginAllowed("127.0.0.1", "user"));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private record WindowValue(long count, Instant expiresAt) {
    }
}

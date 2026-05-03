package dev.knalis.gateway.service;

import dev.knalis.gateway.config.GatewayProperties;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserRateLimiterServiceTest {

    private MutableClock clock;
    private UserRateLimiterService userRateLimiterService;
    private Map<String, WindowValue> redisWindows;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setMaxRequests(2);
        properties.getRateLimit().setWindow(Duration.ofSeconds(30));

        clock = new MutableClock(Instant.parse("2026-04-15T10:15:30Z"));
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
        
        userRateLimiterService = new UserRateLimiterService(
                properties,
                stringRedisTemplate,
                clock,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void allowsRequestsWithinWindowLimit() {
        UUID userId = UUID.randomUUID();

        assertTrue(userRateLimiterService.tryAcquire(userId).allowed());
        assertTrue(userRateLimiterService.tryAcquire(userId).allowed());
    }

    @Test
    void blocksRequestOverLimitAndAllowsAfterWindowReset() {
        UUID userId = UUID.randomUUID();

        userRateLimiterService.tryAcquire(userId);
        userRateLimiterService.tryAcquire(userId);

        UserRateLimiterService.RateLimitDecision blocked = userRateLimiterService.tryAcquire(userId);
        assertFalse(blocked.allowed());
        assertTrue(blocked.retryAfterSeconds() > 0);

        clock.advance(Duration.ofSeconds(31));

        assertTrue(userRateLimiterService.tryAcquire(userId).allowed());
    }

    @Test
    void allowsRequestsWhenRateLimitIsDisabled() {
        GatewayProperties properties = new GatewayProperties();
        properties.getRateLimit().setEnabled(false);
        properties.getRateLimit().setMaxRequests(1);
        properties.getRateLimit().setWindow(Duration.ofSeconds(30));

        UserRateLimiterService disabledService = new UserRateLimiterService(
                properties,
                mock(StringRedisTemplate.class),
                clock,
                new SimpleMeterRegistry()
        );
        UUID userId = UUID.randomUUID();

        assertTrue(disabledService.tryAcquire(userId).allowed());
        assertTrue(disabledService.tryAcquire(userId).allowed());
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

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
    
    private record WindowValue(long count, Instant expiresAt) {
    }
}

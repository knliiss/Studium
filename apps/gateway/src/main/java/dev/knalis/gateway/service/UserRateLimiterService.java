package dev.knalis.gateway.service;

import dev.knalis.gateway.config.GatewayProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserRateLimiterService {
    
    private static final DefaultRedisScript<List> INCREMENT_WINDOW_SCRIPT = new DefaultRedisScript<>(
            """
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    local ttl = redis.call('PTTL', KEYS[1])
                    return { current, ttl }
                    """,
            List.class
    );

    private final GatewayProperties gatewayProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public boolean isEnabled() {
        return gatewayProperties.getRateLimit().isEnabled();
    }

    public RateLimitDecision tryAcquire(UUID userId) {
        if (!isEnabled()) {
            return new RateLimitDecision(true, 0);
        }

        Instant now = Instant.now(clock);
        Duration window = gatewayProperties.getRateLimit().getWindow();
        int maxRequests = gatewayProperties.getRateLimit().getMaxRequests();
        long windowMillis = window.toMillis();
        long nowMillis = now.toEpochMilli();
        long windowStart = nowMillis - (nowMillis % windowMillis);
        long ttlMillis = Math.max(1L, windowStart + windowMillis - nowMillis);
        
        @SuppressWarnings("unchecked")
        List<Long> result = stringRedisTemplate.execute(
                INCREMENT_WINDOW_SCRIPT,
                List.of(key(userId, windowStart)),
                Long.toString(ttlMillis)
        );
        long currentCount = result != null && !result.isEmpty() ? result.get(0) : 0L;
        long retryAfterMillis = result != null && result.size() > 1 ? result.get(1) : ttlMillis;
        
        if (currentCount <= maxRequests) {
            meterRegistry.counter("app.gateway.rate_limit.requests", "outcome", "allowed").increment();
            return new RateLimitDecision(true, 0);
        }
        
        meterRegistry.counter("app.gateway.rate_limit.requests", "outcome", "rejected").increment();
        long retryAfterSeconds = Math.max(1, Duration.ofMillis(retryAfterMillis).toSeconds());
        return new RateLimitDecision(false, retryAfterSeconds);
    }
    
    private String key(UUID userId, long windowStart) {
        return gatewayProperties.getRedis().getRateLimitKeyPrefix() + userId + ":" + windowStart;
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    }
}

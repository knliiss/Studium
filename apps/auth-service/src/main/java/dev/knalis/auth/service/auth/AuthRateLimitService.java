package dev.knalis.auth.service.auth;

import dev.knalis.auth.config.AuthProperties;
import dev.knalis.auth.exception.AuthRateLimitExceededException;
import dev.knalis.auth.service.common.TokenHashService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthRateLimitService {

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

    private final AuthProperties authProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final TokenHashService tokenHashService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public void ensureRegisterAllowed(String ipAddress) {
        enforce("register-ip", ipAddress, authProperties.getRateLimit().getRegisterIp());
    }

    public void ensureLoginAllowed(String ipAddress, String username) {
        enforce("login-ip", ipAddress, authProperties.getRateLimit().getLoginIp());
        enforce("login-username", username, authProperties.getRateLimit().getLoginUsername());
    }

    public void ensurePasswordResetAllowed(String ipAddress, String email) {
        enforce("password-reset-ip", ipAddress, authProperties.getRateLimit().getPasswordResetIp());
        enforce("password-reset-email", email, authProperties.getRateLimit().getPasswordResetEmail());
    }

    public void ensureMfaDispatchAllowed(String ipAddress, String challengeToken) {
        enforce("mfa-dispatch-ip", ipAddress, authProperties.getRateLimit().getMfaDispatch());
        enforce("mfa-dispatch-challenge", challengeToken, authProperties.getRateLimit().getMfaDispatch());
    }

    public void ensureMfaVerifyAllowed(String ipAddress, String challengeToken) {
        enforce("mfa-verify-ip", ipAddress, authProperties.getRateLimit().getMfaVerify());
        enforce("mfa-verify-challenge", challengeToken, authProperties.getRateLimit().getMfaVerify());
    }

    private void enforce(String scope, String rawSubject, AuthProperties.Bucket bucket) {
        if (!authProperties.getRateLimit().isEnabled()) {
            return;
        }

        RateLimitDecision decision = tryAcquire(scope, normalizedSubject(rawSubject), bucket);
        meterRegistry.counter(
                "app.auth.rate_limit.requests",
                "scope", scope,
                "outcome", decision.allowed() ? "allowed" : "rejected"
        ).increment();
        if (!decision.allowed()) {
            throw new AuthRateLimitExceededException(scope, decision.retryAfterSeconds());
        }
    }

    private RateLimitDecision tryAcquire(String scope, String subject, AuthProperties.Bucket bucket) {
        Instant now = Instant.now(clock);
        long windowMillis = bucket.getWindow().toMillis();
        long nowMillis = now.toEpochMilli();
        long windowStart = nowMillis - (nowMillis % windowMillis);
        long ttlMillis = Math.max(1L, windowStart + windowMillis - nowMillis);

        @SuppressWarnings("unchecked")
        List<Long> result = stringRedisTemplate.execute(
                INCREMENT_WINDOW_SCRIPT,
                List.of(key(scope, subject, windowStart)),
                Long.toString(ttlMillis)
        );
        long currentCount = result != null && !result.isEmpty() ? result.get(0) : 0L;
        long retryAfterMillis = result != null && result.size() > 1 ? result.get(1) : ttlMillis;

        if (currentCount <= bucket.getMaxRequests()) {
            return new RateLimitDecision(true, 0);
        }
        return new RateLimitDecision(false, Math.max(1, Duration.ofMillis(retryAfterMillis).toSeconds()));
    }

    private String key(String scope, String subject, long windowStart) {
        return authProperties.getRateLimit().getRedisKeyPrefix()
                + scope
                + ":"
                + tokenHashService.hash(subject)
                + ":"
                + windowStart;
    }

    private String normalizedSubject(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    }
}

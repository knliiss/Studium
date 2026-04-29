package dev.knalis.gateway.service;

import dev.knalis.contracts.event.UserBannedEvent;
import dev.knalis.contracts.event.UserUnbannedEvent;
import dev.knalis.gateway.config.GatewayProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BanStateService {
    
    private static final String FIELD_ACTIVE = "active";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    private static final String FIELD_OCCURRED_AT = "occurredAt";
    private static final DefaultRedisScript<Long> UPSERT_STATE_SCRIPT = new DefaultRedisScript<>(
            """
                    local currentTs = redis.call('HGET', KEYS[1], 'occurredAt')
                    if currentTs and tonumber(currentTs) > tonumber(ARGV[4]) then
                        return 0
                    end
                    redis.call('HSET', KEYS[1],
                        'active', ARGV[1],
                        'reason', ARGV[2],
                        'expiresAt', ARGV[3],
                        'occurredAt', ARGV[4]
                    )
                    return 1
                    """,
            Long.class
    );
    
    private final GatewayProperties gatewayProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public void rememberBan(UserBannedEvent event) {
        upsertState(new BanState(true, event.reason(), event.expiresAt(), event.occurredAt()), event.userId());
        meterRegistry.counter("app.gateway.ban_state.updated", "state", "banned").increment();
        log.info("Remembered distributed ban state for userId={}, expiresAt={}", event.userId(), event.expiresAt());
    }

    public void rememberUnban(UserUnbannedEvent event) {
        upsertState(new BanState(false, null, null, event.occurredAt()), event.userId());
        meterRegistry.counter("app.gateway.ban_state.updated", "state", "unbanned").increment();
        log.info("Remembered distributed unban state for userId={}", event.userId());
    }

    public Optional<BanState> findActiveBan(UUID userId) {
        Map<Object, Object> fields = hashOperations().entries(key(userId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        
        BanState state = new BanState(
                Boolean.parseBoolean(String.valueOf(fields.getOrDefault(FIELD_ACTIVE, "false"))),
                readNullableString(fields.get(FIELD_REASON)),
                readNullableInstant(fields.get(FIELD_EXPIRES_AT)),
                Instant.ofEpochMilli(Long.parseLong(String.valueOf(fields.get(FIELD_OCCURRED_AT))))
        );
        if (!state.active()) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        if (state.expiresAt() != null && !state.expiresAt().isAfter(now)) {
            return Optional.empty();
        }

        return Optional.of(state);
    }
    
    private void upsertState(BanState state, UUID userId) {
        stringRedisTemplate.execute(
                UPSERT_STATE_SCRIPT,
                List.of(key(userId)),
                Boolean.toString(state.active()),
                state.reason() == null ? "" : state.reason(),
                state.expiresAt() == null ? "" : Long.toString(state.expiresAt().toEpochMilli()),
                Long.toString(state.occurredAt().toEpochMilli())
        );
    }
    
    private String key(UUID userId) {
        return gatewayProperties.getRedis().getBanStateKeyPrefix() + userId;
    }
    
    private HashOperations<String, Object, Object> hashOperations() {
        return stringRedisTemplate.opsForHash();
    }
    
    private String readNullableString(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = value.toString();
        return stringValue.isBlank() ? null : stringValue;
    }
    
    private Instant readNullableInstant(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = value.toString();
        return stringValue.isBlank() ? null : Instant.ofEpochMilli(Long.parseLong(stringValue));
    }
    
    public record BanState(boolean active, String reason, Instant expiresAt, Instant occurredAt) {
    
    }
}

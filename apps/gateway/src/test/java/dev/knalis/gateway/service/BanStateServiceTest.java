package dev.knalis.gateway.service;

import dev.knalis.contracts.event.UserBannedEvent;
import dev.knalis.contracts.event.UserUnbannedEvent;
import dev.knalis.gateway.config.GatewayProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
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

class BanStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-15T10:15:30Z");

    private BanStateService banStateService;
    private Map<String, Map<Object, Object>> redisState;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        GatewayProperties properties = new GatewayProperties();
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        
        redisState = new HashMap<>();
        
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(any())).thenAnswer(invocation ->
                redisState.getOrDefault(invocation.getArgument(0), Map.of())
        );
        when(stringRedisTemplate.execute(any(), anyList(), any(), any(), any(), any())).thenAnswer(invocation -> {
            List<String> keys = invocation.getArgument(1);
            String key = keys.getFirst();
            String active = invocation.getArgument(2);
            String reason = invocation.getArgument(3);
            String expiresAt = invocation.getArgument(4);
            String occurredAt = invocation.getArgument(5);
            
            Map<Object, Object> current = redisState.computeIfAbsent(key, ignored -> new HashMap<>());
            Object currentOccurredAt = current.get("occurredAt");
            if (currentOccurredAt != null && Long.parseLong(currentOccurredAt.toString()) > Long.parseLong(occurredAt)) {
                return 0L;
            }
            
            current.put("active", active);
            current.put("reason", reason);
            current.put("expiresAt", expiresAt);
            current.put("occurredAt", occurredAt);
            return 1L;
        });
        
        banStateService = new BanStateService(
                properties,
                stringRedisTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void findActiveBanReturnsRememberedBan() {
        UUID userId = UUID.randomUUID();

        banStateService.rememberBan(new UserBannedEvent(
                UUID.randomUUID(),
                userId,
                "user@example.com",
                "user",
                "spam",
                NOW.plusSeconds(300),
                UUID.randomUUID(),
                "admin",
                NOW
        ));

        assertTrue(banStateService.findActiveBan(userId).isPresent());
    }

    @Test
    void rememberUnbanClearsActiveBan() {
        UUID userId = UUID.randomUUID();

        banStateService.rememberBan(new UserBannedEvent(
                UUID.randomUUID(),
                userId,
                "user@example.com",
                "user",
                "spam",
                null,
                UUID.randomUUID(),
                "admin",
                NOW.minusSeconds(60)
        ));
        banStateService.rememberUnban(new UserUnbannedEvent(
                UUID.randomUUID(),
                userId,
                "user@example.com",
                "user",
                UUID.randomUUID(),
                "admin",
                NOW
        ));

        assertFalse(banStateService.findActiveBan(userId).isPresent());
    }

    @Test
    void olderBanEventDoesNotOverrideNewerUnban() {
        UUID userId = UUID.randomUUID();

        banStateService.rememberUnban(new UserUnbannedEvent(
                UUID.randomUUID(),
                userId,
                "user@example.com",
                "user",
                UUID.randomUUID(),
                "admin",
                NOW
        ));
        banStateService.rememberBan(new UserBannedEvent(
                UUID.randomUUID(),
                userId,
                "user@example.com",
                "user",
                "old ban",
                null,
                UUID.randomUUID(),
                "admin",
                NOW.minusSeconds(60)
        ));

        assertFalse(banStateService.findActiveBan(userId).isPresent());
    }
}

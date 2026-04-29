package dev.knalis.profile.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.knalis.profile.dto.response.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ProfileCacheConfigTest {

    private final ProfileCacheConfig profileCacheConfig = new ProfileCacheConfig();

    @Test
    void redisCacheValueSerializerRoundTripsProfileResponseWithInstants() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        GenericJackson2JsonRedisSerializer serializer = profileCacheConfig.redisCacheValueSerializer(objectMapper);
        UserProfileResponse response = new UserProfileResponse(
                UUID.randomUUID(),
                "cached-user",
                "cached-user@example.com",
                "Cached User",
                null,
                "en-US",
                "Europe/Kiev",
                Instant.parse("2026-04-17T18:30:00Z"),
                Instant.parse("2026-04-17T18:31:00Z")
        );

        Object deserialized = serializer.deserialize(serializer.serialize(response));

        assertInstanceOf(UserProfileResponse.class, deserialized);
        assertEquals(response, deserialized);
    }
}

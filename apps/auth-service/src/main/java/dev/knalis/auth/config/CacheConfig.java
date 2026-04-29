package dev.knalis.auth.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
public class CacheConfig {
    
    public static final String ACTIVE_BANS_CACHE = "activeBans";
    public static final String MFA_ENABLED_FLAGS_CACHE = "mfaEnabledFlags";
    public static final String MFA_ENABLED_METHODS_CACHE = "mfaEnabledMethods";
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()
                ));
        
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        ACTIVE_BANS_CACHE,
                        defaultConfig.entryTtl(Duration.ofMinutes(5)),
                        MFA_ENABLED_FLAGS_CACHE,
                        defaultConfig.entryTtl(Duration.ofMinutes(5)),
                        MFA_ENABLED_METHODS_CACHE,
                        defaultConfig.entryTtl(Duration.ofMinutes(5))
                ))
                .transactionAware()
                .build();
    }
}

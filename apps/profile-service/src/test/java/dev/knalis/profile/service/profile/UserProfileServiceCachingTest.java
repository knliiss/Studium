package dev.knalis.profile.service.profile;

import dev.knalis.profile.client.FileServiceClient;
import dev.knalis.profile.config.ProfileCacheConfig;
import dev.knalis.profile.dto.request.UpdateMyProfileRequest;
import dev.knalis.profile.dto.response.UserProfileResponse;
import dev.knalis.profile.entity.UserProfile;
import dev.knalis.profile.factory.profile.UserProfileFactory;
import dev.knalis.profile.mapper.UserProfileMapper;
import dev.knalis.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserProfileServiceCachingTest.TestConfig.class)
class UserProfileServiceCachingTest {

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserProfileMapper userProfileMapper;

    private UUID userId;
    private UserProfile profile;
    private UserProfileResponse initialResponse;
    private UserProfileResponse updatedResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername("cached-user");
        profile.setDisplayName("Cached User");
        profile.setLocale("en");
        profile.setTimezone("UTC");
        profile.setCreatedAt(Instant.parse("2026-04-15T10:15:30Z"));
        profile.setUpdatedAt(Instant.parse("2026-04-15T10:15:30Z"));

        initialResponse = new UserProfileResponse(
                userId,
                "cached-user",
                null,
                "Cached User",
                null,
                "en",
                "UTC",
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
        updatedResponse = new UserProfileResponse(
                userId,
                "cached-user",
                null,
                "Updated User",
                null,
                "en",
                "UTC",
                profile.getCreatedAt(),
                Instant.parse("2026-04-15T10:16:30Z")
        );

        Mockito.reset(userProfileRepository, userProfileMapper);
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userProfileMapper.toResponse(profile)).thenReturn(initialResponse, updatedResponse);
    }

    @Test
    void getOrCreateMyProfileUsesCacheForRepeatedReads() {
        UserProfileResponse first = userProfileService.getOrCreateMyProfile(userId, "cached-user");
        UserProfileResponse second = userProfileService.getOrCreateMyProfile(userId, "cached-user");

        assertEquals(first, second);
        verify(userProfileRepository, times(1)).findByUserId(userId);
        verify(userProfileMapper, times(1)).toResponse(profile);
    }

    @Test
    void updateMyProfileRefreshesCachedValue() {
        userProfileService.getOrCreateMyProfile(userId, "cached-user");

        UserProfileResponse updated = userProfileService.updateMyProfile(
                userId,
                "cached-user",
                new UpdateMyProfileRequest("Updated User", null, null)
        );
        UserProfileResponse cachedAfterUpdate = userProfileService.getOrCreateMyProfile(userId, "cached-user");

        assertEquals(updated, cachedAfterUpdate);
        verify(userProfileRepository, times(2)).findByUserId(userId);
        verify(userProfileRepository, times(1)).save(profile);
    }

    @Configuration
    @EnableCaching
    static class TestConfig {
        
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(ProfileCacheConfig.USER_PROFILES_CACHE);
        }

        @Bean
        UserProfileRepository userProfileRepository() {
            return Mockito.mock(UserProfileRepository.class);
        }

        @Bean
        UserProfileMapper userProfileMapper() {
            return Mockito.mock(UserProfileMapper.class);
        }
        
        @Bean
        FileServiceClient fileServiceClient() {
            return Mockito.mock(FileServiceClient.class);
        }

        @Bean
        UserProfileFactory userProfileFactory() {
            return new UserProfileFactory();
        }

        @Bean
        UserProfileService userProfileService(
                UserProfileRepository userProfileRepository,
                UserProfileFactory userProfileFactory,
                UserProfileMapper userProfileMapper,
                FileServiceClient fileServiceClient
        ) {
            return new UserProfileService(userProfileRepository, userProfileFactory, userProfileMapper, fileServiceClient);
        }
    }
}

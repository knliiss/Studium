package dev.knalis.profile.service.profile;

import dev.knalis.contracts.event.UserEmailChangedEvent;
import dev.knalis.contracts.event.UserRegisteredEvent;
import dev.knalis.contracts.event.UserUsernameChangedEvent;
import dev.knalis.profile.client.FileServiceClient;
import dev.knalis.profile.client.dto.RemoteStoredFileResponse;
import dev.knalis.profile.dto.request.UpdateAvatarRequest;
import dev.knalis.profile.dto.request.UpdateMyProfileRequest;
import dev.knalis.profile.dto.response.UserProfileResponse;
import dev.knalis.profile.entity.UserProfile;
import dev.knalis.profile.exception.InvalidAvatarFileException;
import dev.knalis.profile.factory.profile.UserProfileFactory;
import dev.knalis.profile.mapper.UserProfileMapper;
import dev.knalis.profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static dev.knalis.profile.config.ProfileCacheConfig.USER_PROFILES_CACHE;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {
    
    private final UserProfileRepository userProfileRepository;
    private final UserProfileFactory userProfileFactory;
    private final UserProfileMapper userProfileMapper;
    private final FileServiceClient fileServiceClient;

    @Transactional
    public void createProfileForRegisteredUser(UserRegisteredEvent event) {
        if (userProfileRepository.existsByUserId(event.userId())) {
            log.debug("Profile already exists for userId={}", event.userId());
            return;
        }

        try {
            userProfileRepository.save(
                    userProfileFactory.newDefaultProfile(event.userId(), event.username(), event.email(), event.username())
            );
            log.info("Created profile for registered userId={}", event.userId());
        } catch (DataIntegrityViolationException exception) {
            log.info("Skipped duplicate profile creation for userId={}", event.userId());
        }
    }
    
    @Transactional
    public void syncEmail(UserEmailChangedEvent event) {
        userProfileRepository.findByUserId(event.userId()).ifPresentOrElse(profile -> {
            profile.setEmail(event.newEmail());
            userProfileRepository.save(profile);
            log.info("Synchronized email for userId={}", event.userId());
        }, () -> log.warn("Skipped email sync because profile does not exist for userId={}", event.userId()));
    }
    
    @Transactional
    public void syncUsername(UserUsernameChangedEvent event) {
        userProfileRepository.findByUserId(event.userId()).ifPresentOrElse(profile -> {
            profile.setUsername(event.newUsername());
            if (profile.getDisplayName() == null
                    || profile.getDisplayName().isBlank()
                    || profile.getDisplayName().equals(event.oldUsername())) {
                profile.setDisplayName(event.newUsername());
            }
            userProfileRepository.save(profile);
            log.info("Synchronized username for userId={}", event.userId());
        }, () -> log.warn("Skipped username sync because profile does not exist for userId={}", event.userId()));
    }
    
    @Transactional
    @Cacheable(cacheNames = USER_PROFILES_CACHE, key = "#userId", sync = true)
    public UserProfileResponse getOrCreateMyProfile(UUID userId, String usernameFallback) {
        UserProfile profile = getOrCreateProfile(userId, usernameFallback);
        
        return userProfileMapper.toResponse(profile);
    }
    
    @Transactional
    @CachePut(cacheNames = USER_PROFILES_CACHE, key = "#userId")
    public UserProfileResponse updateMyProfile(UUID userId, String usernameFallback, UpdateMyProfileRequest request) {
        UserProfile profile = getOrCreateProfile(userId, usernameFallback);
        
        if (request.displayName() != null && !request.displayName().isBlank()) {
            profile.setDisplayName(request.displayName().trim());
        }
        
        if (request.locale() != null && !request.locale().isBlank()) {
            profile.setLocale(request.locale().trim());
        }
        
        if (request.timezone() != null && !request.timezone().isBlank()) {
            profile.setTimezone(request.timezone().trim());
        }
        
        return userProfileMapper.toResponse(userProfileRepository.save(profile));
    }
    
    @Transactional
    @CachePut(cacheNames = USER_PROFILES_CACHE, key = "#userId")
    public UserProfileResponse updateMyAvatar(
            UUID userId,
            String usernameFallback,
            String bearerToken,
            UpdateAvatarRequest request
    ) {
        UserProfile profile = getOrCreateProfile(userId, usernameFallback);
        RemoteStoredFileResponse file = fileServiceClient.getMyFile(bearerToken, request.fileId());
        validateAvatarFile(userId, file);
        
        Optional<UUID> previousAvatarFileId = parseAvatarFileId(profile.getAvatarFileKey());
        if (previousAvatarFileId.isPresent() && !previousAvatarFileId.get().equals(request.fileId())) {
            fileServiceClient.markFileOrphaned(bearerToken, previousAvatarFileId.get());
        }
        
        fileServiceClient.markFileActive(bearerToken, request.fileId());
        profile.setAvatarFileKey(request.fileId().toString());
        
        return userProfileMapper.toResponse(userProfileRepository.save(profile));
    }
    
    @Transactional
    @CachePut(cacheNames = USER_PROFILES_CACHE, key = "#userId")
    public UserProfileResponse removeMyAvatar(
            UUID userId,
            String usernameFallback,
            String bearerToken
    ) {
        UserProfile profile = getOrCreateProfile(userId, usernameFallback);
        Optional<UUID> avatarFileId = parseAvatarFileId(profile.getAvatarFileKey());
        if (avatarFileId.isPresent()) {
            fileServiceClient.markFileOrphaned(bearerToken, avatarFileId.get());
            profile.setAvatarFileKey(null);
        } else if (profile.getAvatarFileKey() != null && !profile.getAvatarFileKey().isBlank()) {
            log.warn("Skipped avatar lifecycle transition because avatarFileKey is not a file id for userId={}", userId);
            profile.setAvatarFileKey(null);
        }
        return userProfileMapper.toResponse(userProfileRepository.save(profile));
    }

    private UserProfile getOrCreateProfile(UUID userId, String usernameFallback) {
        return userProfileRepository.findByUserId(userId)
                .orElseGet(() -> userProfileRepository.save(
                        userProfileFactory.newDefaultProfile(userId, usernameFallback, null, usernameFallback)
                ));
    }
    
    private void validateAvatarFile(UUID userId, RemoteStoredFileResponse file) {
        if (!userId.equals(file.ownerId())) {
            throw new InvalidAvatarFileException(file.id(), "Avatar file belongs to a different user");
        }
        if (!"AVATAR".equalsIgnoreCase(file.fileKind())) {
            throw new InvalidAvatarFileException(file.id(), "Only files with kind AVATAR can be assigned as profile avatar");
        }
    }
    
    private Optional<UUID> parseAvatarFileId(String avatarFileKey) {
        if (avatarFileKey == null || avatarFileKey.isBlank()) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(UUID.fromString(avatarFileKey));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

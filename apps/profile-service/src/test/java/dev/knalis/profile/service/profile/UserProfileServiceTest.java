package dev.knalis.profile.service.profile;

import dev.knalis.contracts.event.UserEmailChangedEvent;
import dev.knalis.contracts.event.UserRegisteredEvent;
import dev.knalis.contracts.event.UserUsernameChangedEvent;
import dev.knalis.profile.client.FileServiceClient;
import dev.knalis.profile.client.dto.RemoteStoredFileResponse;
import dev.knalis.profile.dto.request.UpdateAvatarRequest;
import dev.knalis.profile.dto.response.UserProfileResponse;
import dev.knalis.profile.entity.UserProfile;
import dev.knalis.profile.exception.InvalidAvatarFileException;
import dev.knalis.profile.factory.profile.UserProfileFactory;
import dev.knalis.profile.mapper.UserProfileMapper;
import dev.knalis.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserProfileMapper userProfileMapper;
    
    @Mock
    private FileServiceClient fileServiceClient;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(
                userProfileRepository,
                new UserProfileFactory(),
                userProfileMapper,
                fileServiceClient
        );
    }

    @Test
    void createProfileForRegisteredUserSavesDefaultProfileWhenMissing() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "new-user",
                "new-user@example.com",
                Instant.now()
        );

        when(userProfileRepository.existsByUserId(event.userId())).thenReturn(false);

        userProfileService.createProfileForRegisteredUser(event);

        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void createProfileForRegisteredUserSkipsSaveWhenProfileAlreadyExists() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "existing-user",
                "existing-user@example.com",
                Instant.now()
        );

        when(userProfileRepository.existsByUserId(event.userId())).thenReturn(true);

        userProfileService.createProfileForRegisteredUser(event);

        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void createProfileForRegisteredUserIgnoresDuplicateInsertRace() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "racy-user",
                "racy-user@example.com",
                Instant.now()
        );

        when(userProfileRepository.existsByUserId(event.userId())).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        userProfileService.createProfileForRegisteredUser(event);

        verify(userProfileRepository).save(any(UserProfile.class));
    }
    
    @Test
    void syncEmailUpdatesExistingProfile() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername("user");
        
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        
        userProfileService.syncEmail(new UserEmailChangedEvent(
                UUID.randomUUID(),
                userId,
                "old@example.com",
                "new@example.com",
                Instant.now()
        ));
        
        verify(userProfileRepository).save(profile);
    }
    
    @Test
    void syncUsernameUpdatesExistingProfile() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername("old-user");
        profile.setDisplayName("old-user");
        
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        
        userProfileService.syncUsername(new UserUsernameChangedEvent(
                UUID.randomUUID(),
                userId,
                "old-user",
                "new-user",
                Instant.now()
        ));
        
        verify(userProfileRepository).save(profile);
    }
    
    @Test
    void syncUsernameSkipsWhenProfileIsMissing() {
        UUID userId = UUID.randomUUID();
        
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        
        userProfileService.syncUsername(new UserUsernameChangedEvent(
                UUID.randomUUID(),
                userId,
                "old-user",
                "new-user",
                Instant.now()
        ));
        
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }
    
    @Test
    void updateMyAvatarMarksPreviousAvatarOrphanedAndNewAvatarActive() {
        UUID userId = UUID.randomUUID();
        UUID previousAvatarId = UUID.randomUUID();
        UUID newAvatarId = UUID.randomUUID();
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername("avatar-user");
        profile.setAvatarFileKey(previousAvatarId.toString());
        
        UserProfileResponse response = new UserProfileResponse(
                userId,
                "avatar-user",
                null,
                "avatar-user",
                newAvatarId.toString(),
                "en",
                "UTC",
                Instant.now(),
                Instant.now()
        );
        
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(profile)).thenReturn(profile);
        when(fileServiceClient.getMyFile("token", newAvatarId)).thenReturn(new RemoteStoredFileResponse(
                newAvatarId,
                userId,
                "avatar.png",
                "image/png",
                1024L,
                "AVATAR",
                "PRIVATE",
                "UPLOADED",
                Instant.now().toString(),
                Instant.now().toString(),
                Instant.now().toString()
        ));
        when(userProfileMapper.toResponse(profile)).thenReturn(response);
        
        userProfileService.updateMyAvatar(
                userId,
                "avatar-user",
                "token",
                new UpdateAvatarRequest(newAvatarId)
        );
        
        verify(fileServiceClient).markFileOrphaned("token", previousAvatarId);
        verify(fileServiceClient).markFileActive("token", newAvatarId);
        verify(userProfileRepository).save(profile);
    }
    
    @Test
    void updateMyAvatarSkipsLegacyAvatarKeyThatIsNotUuid() {
        UUID userId = UUID.randomUUID();
        UUID newAvatarId = UUID.randomUUID();
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername("avatar-user");
        profile.setAvatarFileKey("avatars/legacy-object-key.png");
        
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(profile)).thenReturn(profile);
        when(fileServiceClient.getMyFile("token", newAvatarId)).thenReturn(new RemoteStoredFileResponse(
                newAvatarId,
                userId,
                "avatar.png",
                "image/png",
                1024L,
                "AVATAR",
                "PRIVATE",
                "UPLOADED",
                Instant.now().toString(),
                Instant.now().toString(),
                Instant.now().toString()
        ));
        when(userProfileMapper.toResponse(profile)).thenReturn(new UserProfileResponse(
                userId,
                "avatar-user",
                null,
                "avatar-user",
                newAvatarId.toString(),
                "en",
                "UTC",
                Instant.now(),
                Instant.now()
        ));
        
        userProfileService.updateMyAvatar(
                userId,
                "avatar-user",
                "token",
                new UpdateAvatarRequest(newAvatarId)
        );
        
        verify(fileServiceClient, never()).markFileOrphaned(any(), any());
        verify(fileServiceClient).markFileActive("token", newAvatarId);
    }
    
    @Test
    void updateMyAvatarRejectsForeignFile() {
        UUID userId = UUID.randomUUID();
        UUID avatarId = UUID.randomUUID();
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername("avatar-user");
        
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(fileServiceClient.getMyFile("token", avatarId)).thenReturn(new RemoteStoredFileResponse(
                avatarId,
                UUID.randomUUID(),
                "avatar.png",
                "image/png",
                1024L,
                "AVATAR",
                "PRIVATE",
                "UPLOADED",
                Instant.now().toString(),
                Instant.now().toString(),
                Instant.now().toString()
        ));
        
        assertThrows(InvalidAvatarFileException.class, () -> userProfileService.updateMyAvatar(
                userId,
                "avatar-user",
                "token",
                new UpdateAvatarRequest(avatarId)
        ));
    }
    
    @Test
    void removeMyAvatarClearsLegacyAvatarKeyWithoutLifecycleCall() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername("avatar-user");
        profile.setAvatarFileKey("avatars/legacy-object-key.png");
        
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(profile)).thenReturn(profile);
        when(userProfileMapper.toResponse(profile)).thenReturn(new UserProfileResponse(
                userId,
                "avatar-user",
                null,
                "avatar-user",
                null,
                "en",
                "UTC",
                Instant.now(),
                Instant.now()
        ));
        
        userProfileService.removeMyAvatar(userId, "avatar-user", "token");
        
        verify(fileServiceClient, never()).markFileOrphaned(any(), any());
        verify(userProfileRepository).save(profile);
    }
}

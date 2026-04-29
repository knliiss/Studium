package dev.knalis.profile.factory.profile;

import dev.knalis.profile.entity.UserProfile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserProfileFactory {
    
    public UserProfile newDefaultProfile(UUID userId, String username, String email, String fallbackDisplayName) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername(username);
        profile.setEmail(email);
        profile.setDisplayName(fallbackDisplayName);
        profile.setAvatarFileKey(null);
        profile.setLocale("en");
        profile.setTimezone("UTC");
        return profile;
    }
}

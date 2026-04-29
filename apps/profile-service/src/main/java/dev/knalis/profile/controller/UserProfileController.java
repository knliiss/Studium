package dev.knalis.profile.controller;

import dev.knalis.profile.dto.request.UpdateAvatarRequest;
import dev.knalis.profile.dto.request.UpdateMyProfileRequest;
import dev.knalis.profile.dto.response.UserProfileResponse;
import dev.knalis.profile.service.profile.UserProfileService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {
    
    private final UserProfileService userProfileService;
    private final CurrentUserService currentUserService;
    
    @GetMapping("/me")
    public UserProfileResponse getMyProfile(Authentication authentication) {
        return userProfileService.getOrCreateMyProfile(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentUsername(authentication)
        );
    }
    
    @PatchMapping("/me")
    public UserProfileResponse updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return userProfileService.updateMyProfile(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentUsername(authentication),
                request
        );
    }
    
    @PutMapping("/me/avatar")
    public UserProfileResponse updateMyAvatar(
            Authentication authentication,
            @Valid @RequestBody UpdateAvatarRequest request
    ) {
        return userProfileService.updateMyAvatar(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentUsername(authentication),
                currentUserService.getCurrentTokenValue(authentication),
                request
        );
    }
    
    @DeleteMapping("/me/avatar")
    public UserProfileResponse removeMyAvatar(Authentication authentication) {
        return userProfileService.removeMyAvatar(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentUsername(authentication),
                currentUserService.getCurrentTokenValue(authentication)
        );
    }
}

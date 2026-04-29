package dev.knalis.auth.controller.auth;

import dev.knalis.auth.dto.request.ConfirmTotpSetupRequest;
import dev.knalis.auth.dto.request.DisableMfaMethodRequest;
import dev.knalis.auth.dto.request.MfaDispatchChallengeRequest;
import dev.knalis.auth.dto.request.MfaVerifyChallengeRequest;
import dev.knalis.auth.dto.request.SetupTotpRequest;
import dev.knalis.auth.dto.response.AuthResponse;
import dev.knalis.auth.dto.response.MfaChallengeResponse;
import dev.knalis.auth.dto.response.MfaMethodsResponse;
import dev.knalis.auth.dto.response.TotpSetupResponse;
import dev.knalis.auth.service.auth.AuthRateLimitService;
import dev.knalis.auth.service.mfa.MfaChallengeService;
import dev.knalis.auth.service.mfa.MfaConfigurationService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/mfa")
@RequiredArgsConstructor
public class MfaController {
    
    private final MfaConfigurationService mfaConfigurationService;
    private final MfaChallengeService mfaChallengeService;
    private final CurrentUserService currentUserService;
    private final AuthRateLimitService authRateLimitService;
    
    @GetMapping("/methods")
    public MfaMethodsResponse getMethods(Authentication authentication) {
        return mfaConfigurationService.getMethods(currentUserService.getCurrentUserId(authentication));
    }
    
    @PostMapping("/totp/setup")
    public TotpSetupResponse setupTotp(
            Authentication authentication,
            @Valid @RequestBody SetupTotpRequest request
    ) {
        return mfaConfigurationService.setupTotp(
                currentUserService.getCurrentUserId(authentication),
                request.currentPassword()
        );
    }
    
    @PostMapping("/totp/confirm")
    public MfaMethodsResponse confirmTotp(
            Authentication authentication,
            @Valid @RequestBody ConfirmTotpSetupRequest request
    ) {
        return mfaConfigurationService.confirmTotp(
                currentUserService.getCurrentUserId(authentication),
                request.currentPassword(),
                request.code()
        );
    }
    
    @PostMapping("/methods/disable")
    public MfaMethodsResponse disableMethod(
            Authentication authentication,
            @Valid @RequestBody DisableMfaMethodRequest request
    ) {
        return mfaConfigurationService.disableMethod(
                currentUserService.getCurrentUserId(authentication),
                request.method(),
                request.currentPassword()
        );
    }
    
    @PostMapping("/challenges/dispatch")
    public MfaChallengeResponse dispatch(
            @Valid @RequestBody MfaDispatchChallengeRequest request,
            HttpServletRequest httpServletRequest
    ) {
        authRateLimitService.ensureMfaDispatchAllowed(resolveClientIp(httpServletRequest), request.challengeToken());
        return mfaChallengeService.dispatch(request);
    }
    
    @PostMapping("/challenges/verify")
    public AuthResponse verify(
            @Valid @RequestBody MfaVerifyChallengeRequest request,
            HttpServletRequest httpServletRequest
    ) {
        authRateLimitService.ensureMfaVerifyAllowed(resolveClientIp(httpServletRequest), request.challengeToken());
        return mfaChallengeService.verify(request);
    }
    
    @GetMapping("/challenges/status")
    public MfaChallengeResponse getStatus(@RequestParam String challengeToken) {
        return mfaChallengeService.getStatus(challengeToken);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

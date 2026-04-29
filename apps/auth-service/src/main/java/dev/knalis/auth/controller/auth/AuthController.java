package dev.knalis.auth.controller.auth;

import dev.knalis.auth.dto.request.ChangePasswordRequest;
import dev.knalis.auth.dto.request.ConfirmPasswordResetRequest;
import dev.knalis.auth.dto.request.LoginRequest;
import dev.knalis.auth.dto.request.LogoutRequest;
import dev.knalis.auth.dto.request.RefreshTokenRequest;
import dev.knalis.auth.dto.request.RegisterRequest;
import dev.knalis.auth.dto.request.RequestPasswordResetRequest;
import dev.knalis.auth.dto.request.UpdateEmailRequest;
import dev.knalis.auth.dto.request.UpdateUsernameRequest;
import dev.knalis.auth.dto.response.AcceptedActionResponse;
import dev.knalis.auth.dto.response.AuthResponse;
import dev.knalis.auth.dto.response.UserAuthResponse;
import dev.knalis.auth.service.auth.AuthRateLimitService;
import dev.knalis.auth.service.auth.AuthService;
import dev.knalis.auth.service.token.TokenService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final TokenService tokenService;
    private final CurrentUserService currentUserService;
    private final AuthRateLimitService authRateLimitService;
    
    @PostMapping("/register")
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest
    ) {
        authRateLimitService.ensureRegisterAllowed(resolveClientIp(httpServletRequest));
        return authService.register(request);
    }
    
    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = resolveClientIp(httpServletRequest);
        authRateLimitService.ensureLoginAllowed(clientIp, request.username());
        return authService.login(
                request,
                clientIp,
                httpServletRequest.getHeader("User-Agent")
        );
    }
    
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }
    
    @PostMapping("/logout")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }
    
    @PostMapping("/password-reset/request")
    public ResponseEntity<AcceptedActionResponse> requestPasswordReset(
            @Valid @RequestBody RequestPasswordResetRequest request,
            HttpServletRequest httpServletRequest
    ) {
        authRateLimitService.ensurePasswordResetAllowed(resolveClientIp(httpServletRequest), request.email());
        tokenService.requestPasswordReset(request);
        return ResponseEntity.accepted().body(AcceptedActionResponse.accepted(
                "If an account exists, the password reset request was accepted."
        ));
    }
    
    @PostMapping("/password-reset/confirm")
    public void confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetRequest request) {
        tokenService.confirmPasswordReset(request);
    }
    
    @PatchMapping("/me/username")
    public UserAuthResponse updateUsername(
            Authentication authentication,
            @Valid @RequestBody UpdateUsernameRequest request
    ) {
        return authService.updateUsername(currentUserService.getCurrentUserId(authentication), request);
    }
    
    @PatchMapping("/me/email")
    public UserAuthResponse updateEmail(
            Authentication authentication,
            @Valid @RequestBody UpdateEmailRequest request
    ) {
        return authService.updateEmail(currentUserService.getCurrentUserId(authentication), request);
    }
    
    @PatchMapping("/me/password")
    public void changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(currentUserService.getCurrentUserId(authentication), request);
    }
    
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

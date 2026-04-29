package dev.knalis.auth.service.auth;

import dev.knalis.auth.config.AuthProperties;
import dev.knalis.auth.dto.request.LoginRequest;
import dev.knalis.auth.dto.request.RefreshTokenRequest;
import dev.knalis.auth.dto.request.RegisterRequest;
import dev.knalis.auth.dto.request.UpdateUsernameRequest;
import dev.knalis.auth.dto.response.AuthStatus;
import dev.knalis.auth.dto.response.AuthResponse;
import dev.knalis.auth.dto.response.MfaChallengeResponse;
import dev.knalis.auth.dto.response.UserAuthResponse;
import dev.knalis.auth.entity.RefreshToken;
import dev.knalis.auth.entity.Role;
import dev.knalis.auth.entity.User;
import dev.knalis.auth.exception.MfaReauthenticationRequiredException;
import dev.knalis.auth.factory.user.UserFactory;
import dev.knalis.auth.mapper.auth.UserAuthMapper;
import dev.knalis.auth.mfa.entity.MfaChallengeStatus;
import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.auth.repository.UserRepository;
import dev.knalis.auth.service.common.AuthEventPublisher;
import dev.knalis.auth.service.jwt.JwtService;
import dev.knalis.auth.service.mfa.MfaChallengeService;
import dev.knalis.auth.service.mfa.MfaConfigurationService;
import dev.knalis.auth.service.token.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthEventPublisher authEventPublisher;

    @Mock
    private UserAuthMapper userAuthMapper;
    
    @Mock
    private MfaConfigurationService mfaConfigurationService;
    
    @Mock
    private MfaChallengeService mfaChallengeService;

    @Mock
    private UserAccessValidator userAccessValidator;
    
    private AuthProperties authProperties;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authService = new AuthService(
                userRepository,
                tokenService,
                jwtService,
                authProperties,
                passwordEncoder,
                authEventPublisher,
                new UserFactory(),
                userAuthMapper,
                mfaConfigurationService,
                mfaChallengeService,
                userAccessValidator
        );
    }

    @Test
    void registerNormalizesUsernameAndEmailBeforeChecksAndSave() {
        RegisterRequest request = new RegisterRequest("  user  ", "  USER@example.com  ", "password123");
        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setUsername("user");
        savedUser.setEmail("user@example.com");
        savedUser.setRoles(Set.of(Role.USER));

        when(passwordEncoder.encode(request.password())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenService.createRefreshToken(savedUser.getId(), false, null))
                .thenReturn(new TokenService.CreatedRefreshToken(null, "refresh-token"));
        when(jwtService.generateAccessToken(savedUser, false, null)).thenReturn("access-token");
        when(userAuthMapper.toResponse(savedUser))
                .thenReturn(new UserAuthResponse(savedUser.getId(), "user", "user@example.com", Set.of(Role.USER), false));

        authService.register(request);

        verify(userRepository).existsByUsernameIgnoreCase("user");
        verify(userRepository).existsByEmailIgnoreCase("user@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUsernameNormalizesValueBeforeUniquenessCheck() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("old-name");
        user.setEmail("user@example.com");
        user.setRoles(Set.of(Role.USER));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userAuthMapper.toResponse(user))
                .thenReturn(new UserAuthResponse(userId, "new-name", "user@example.com", Set.of(Role.USER), false));

        authService.updateUsername(userId, new UpdateUsernameRequest("  new-name  "));

        verify(userRepository).existsByUsernameIgnoreCase("new-name");
        verify(userRepository).save(eq(user));
        verify(authEventPublisher).publishUserUsernameChanged(any());
    }
    
    @Test
    void loginReturnsMfaChallengeWhenUserHasEnabledMethods() {
        LoginRequest request = new LoginRequest("user", "password123");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("user");
        user.setEmail("user@example.com");
        user.setPasswordHash("encoded");
        user.setRoles(Set.of(Role.USER));
        
        when(userRepository.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(mfaConfigurationService.isMfaEnabledForUser(user.getId())).thenReturn(true);
        when(mfaChallengeService.startChallenge(user, "127.0.0.1", "JUnit"))
                .thenReturn(new MfaChallengeResponse(
                        "challenge-token",
                        MfaChallengeStatus.PENDING_SELECTION,
                        List.of(MfaMethodType.TOTP),
                        null,
                        Instant.now().plusSeconds(300),
                        null,
                        null
                ));
        
        AuthResponse response = authService.login(request, "127.0.0.1", "JUnit");
        
        assertEquals(AuthStatus.MFA_REQUIRED, response.status());
        assertEquals("challenge-token", response.mfaChallenge().challengeToken());
    }

    @Test
    void refreshRequiresReauthenticationWhenMfaWasEnabledAfterTokenIssue() {
        UUID userId = UUID.randomUUID();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setMfaVerified(false);

        User user = new User();
        user.setId(userId);
        user.setUsername("user");
        user.setEmail("user@example.com");
        user.setRoles(Set.of(Role.USER));

        when(tokenService.requireValidRefreshToken("refresh-token")).thenReturn(refreshToken);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mfaConfigurationService.isMfaEnabledForUser(userId)).thenReturn(true);

        assertThrows(MfaReauthenticationRequiredException.class, () ->
                authService.refresh(new RefreshTokenRequest("refresh-token")));
        verify(tokenService).revokeRefreshToken("refresh-token");
    }
}

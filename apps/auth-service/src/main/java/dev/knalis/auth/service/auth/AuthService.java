package dev.knalis.auth.service.auth;

import dev.knalis.auth.config.AuthProperties;
import dev.knalis.auth.dto.request.ChangePasswordRequest;
import dev.knalis.auth.dto.request.LoginRequest;
import dev.knalis.auth.dto.request.RefreshTokenRequest;
import dev.knalis.auth.dto.request.RegisterRequest;
import dev.knalis.auth.dto.request.UpdateEmailRequest;
import dev.knalis.auth.dto.request.UpdateUsernameRequest;
import dev.knalis.auth.dto.response.AuthResponse;
import dev.knalis.auth.dto.response.UserAuthResponse;
import dev.knalis.auth.entity.RefreshToken;
import dev.knalis.auth.entity.User;
import dev.knalis.auth.exception.InvalidCredentialsException;
import dev.knalis.auth.exception.MfaReauthenticationRequiredException;
import dev.knalis.auth.exception.UserAlreadyExistsException;
import dev.knalis.auth.exception.UserNotFoundException;
import dev.knalis.auth.factory.user.UserFactory;
import dev.knalis.auth.mapper.auth.UserAuthMapper;
import dev.knalis.auth.repository.UserRepository;
import dev.knalis.auth.service.common.AuthEventPublisher;
import dev.knalis.auth.service.jwt.JwtService;
import dev.knalis.auth.service.mfa.MfaChallengeService;
import dev.knalis.auth.service.mfa.MfaConfigurationService;
import dev.knalis.auth.service.token.TokenService;
import dev.knalis.contracts.event.UserEmailChangedEvent;
import dev.knalis.contracts.event.UserRegisteredEvent;
import dev.knalis.contracts.event.UserUsernameChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher authEventPublisher;
    private final UserFactory userFactory;
    private final UserAuthMapper userAuthMapper;
    private final MfaConfigurationService mfaConfigurationService;
    private final MfaChallengeService mfaChallengeService;
    private final UserAccessValidator userAccessValidator;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedUsername = request.username().trim();
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UserAlreadyExistsException("username", normalizedUsername);
        }
        
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new UserAlreadyExistsException("email", normalizedEmail);
        }
        
        User user = userFactory.newRegisteredUser(
                normalizedUsername,
                normalizedEmail,
                passwordEncoder.encode(request.password())
        );
        
        User savedUser = userRepository.save(user);
        TokenService.CreatedRefreshToken createdRefreshToken = tokenService.createRefreshToken(savedUser.getId(), false, null);
        
        authEventPublisher.publishUserRegistered(
                new UserRegisteredEvent(
                        UUID.randomUUID(),
                        savedUser.getId(),
                        savedUser.getUsername(),
                        savedUser.getEmail(),
                        Instant.now()
                )
        );
        
        return AuthResponse.authenticated(
                jwtService.generateAccessToken(savedUser, false, null),
                createdRefreshToken.rawToken(),
                userAuthMapper.toResponse(savedUser)
        );
    }
    
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(InvalidCredentialsException::new);
        
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        
        userAccessValidator.validate(user);
        if (authProperties.getMfa().isEnabled() && mfaConfigurationService.isMfaEnabledForUser(user.getId())) {
            return AuthResponse.mfaRequired(mfaChallengeService.startChallenge(user, ipAddress, userAgent));
        }
        
        TokenService.CreatedRefreshToken createdRefreshToken = tokenService.createRefreshToken(user.getId(), false, null);
        
        return AuthResponse.authenticated(
                jwtService.generateAccessToken(user, false, null),
                createdRefreshToken.rawToken(),
                userAuthMapper.toResponse(user)
        );
    }
    
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = tokenService.requireValidRefreshToken(request.refreshToken());
        
        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(InvalidCredentialsException::new);
        
        userAccessValidator.validate(user);
        if (authProperties.getMfa().isEnabled()
                && mfaConfigurationService.isMfaEnabledForUser(user.getId())
                && !refreshToken.isMfaVerified()) {
            tokenService.revokeRefreshToken(request.refreshToken());
            throw new MfaReauthenticationRequiredException();
        }

        TokenService.CreatedRefreshToken newRefreshToken = tokenService.rotateRefreshToken(refreshToken);
        
        return AuthResponse.authenticated(
                jwtService.generateAccessToken(user, newRefreshToken.entity().isMfaVerified(), newRefreshToken.entity().getMfaMethod()),
                newRefreshToken.rawToken(),
                userAuthMapper.toResponse(user)
        );
    }
    
    @Transactional
    public void logout(String refreshToken) {
        tokenService.revokeRefreshToken(refreshToken);
    }
    
    @Transactional
    public UserAuthResponse updateUsername(UUID userId, UpdateUsernameRequest request) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        String oldUsername = user.getUsername();
        String normalizedUsername = request.username().trim();
        
        if (!oldUsername.equalsIgnoreCase(normalizedUsername)
                && userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UserAlreadyExistsException("username", normalizedUsername);
        }
        
        user.setUsername(normalizedUsername);
        User saved = userRepository.save(user);
        
        authEventPublisher.publishUserUsernameChanged(
                new UserUsernameChangedEvent(
                        UUID.randomUUID(),
                        saved.getId(),
                        oldUsername,
                        saved.getUsername(),
                        Instant.now()
                )
        );
        
        return userAuthMapper.toResponse(saved);
    }
    
    @Transactional
    public UserAuthResponse updateEmail(UUID userId, UpdateEmailRequest request) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        String oldEmail = user.getEmail();
        String normalizedEmail = request.email().trim().toLowerCase();
        
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail)
                && userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new UserAlreadyExistsException("email", normalizedEmail);
        }
        
        user.setEmail(normalizedEmail);
        User saved = userRepository.save(user);
        
        authEventPublisher.publishUserEmailChanged(
                new UserEmailChangedEvent(
                        UUID.randomUUID(),
                        saved.getId(),
                        oldEmail,
                        saved.getEmail(),
                        Instant.now()
                )
        );
        
        return userAuthMapper.toResponse(saved);
    }
    
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setForcePasswordChange(false);
        userRepository.save(user);
        
        tokenService.revokeAllRefreshTokens(userId);
    }
    
}

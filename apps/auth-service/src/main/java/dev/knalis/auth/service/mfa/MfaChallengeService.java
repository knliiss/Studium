package dev.knalis.auth.service.mfa;

import dev.knalis.auth.config.AuthProperties;
import dev.knalis.auth.dto.request.MfaDispatchChallengeRequest;
import dev.knalis.auth.dto.request.MfaVerifyChallengeRequest;
import dev.knalis.auth.dto.response.AuthResponse;
import dev.knalis.auth.dto.response.MfaChallengeResponse;
import dev.knalis.auth.entity.User;
import dev.knalis.auth.exception.MfaChallengeExpiredException;
import dev.knalis.auth.exception.MfaChallengeNotFoundException;
import dev.knalis.auth.exception.MfaChallengeResolvedException;
import dev.knalis.auth.exception.MfaMethodUnavailableException;
import dev.knalis.auth.exception.MfaVerificationFailedException;
import dev.knalis.auth.exception.UserNotFoundException;
import dev.knalis.auth.mapper.auth.UserAuthMapper;
import dev.knalis.auth.mfa.entity.MfaChallenge;
import dev.knalis.auth.mfa.entity.MfaChallengeStatus;
import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.auth.mfa.entity.UserMfaMethod;
import dev.knalis.auth.mfa.repository.MfaChallengeRepository;
import dev.knalis.auth.repository.UserRepository;
import dev.knalis.auth.service.auth.UserAccessValidator;
import dev.knalis.auth.service.common.TokenHashService;
import dev.knalis.auth.service.jwt.JwtService;
import dev.knalis.auth.service.token.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MfaChallengeService {
    
    private final AuthProperties authProperties;
    private final MfaChallengeRepository mfaChallengeRepository;
    private final UserRepository userRepository;
    private final MfaConfigurationService mfaConfigurationService;
    private final MfaMethodRegistry mfaMethodRegistry;
    private final TokenHashService tokenHashService;
    private final TokenService tokenService;
    private final JwtService jwtService;
    private final UserAuthMapper userAuthMapper;
    private final UserAccessValidator userAccessValidator;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Transactional
    public MfaChallengeResponse startChallenge(User user, String ipAddress, String userAgent) {
        List<UserMfaMethod> enabledMethods = mfaConfigurationService.getEnabledMethods(user.getId());
        if (enabledMethods.isEmpty()) {
            throw new MfaMethodUnavailableException(MfaMethodType.TOTP);
        }
        
        mfaChallengeRepository.cancelPendingForUser(user.getId(), Instant.now());

        String rawChallengeToken = generateChallengeToken();
        MfaChallenge challenge = new MfaChallenge();
        challenge.setUserId(user.getId());
        challenge.setTokenHash(tokenHashService.hash(rawChallengeToken));
        challenge.setAvailableMethodsCsv(enabledMethods.stream().map(UserMfaMethod::getMethodType).map(Enum::name).sorted().reduce((left, right) -> left + "," + right).orElse(""));
        challenge.setStatus(MfaChallengeStatus.PENDING_SELECTION);
        challenge.setIpAddress(ipAddress);
        challenge.setUserAgent(userAgent);
        challenge.setExpiresAt(Instant.now().plus(authProperties.getMfa().getChallengeTtl()));
        mfaChallengeRepository.save(challenge);
        
        return toResponse(challenge, user, null, rawChallengeToken);
    }
    
    @Transactional
    public MfaChallengeResponse dispatch(MfaDispatchChallengeRequest request) {
        String normalizedToken = request.challengeToken().trim();
        MfaChallenge challenge = requireMutableActiveChallenge(normalizedToken);
        User user = requireUser(challenge.getUserId());
        UserMfaMethod method = requireAvailableMethod(challenge, request.method());
        MfaMethodHandler handler = mfaMethodRegistry.getRequired(request.method());
        
        challenge.setSelectedMethod(request.method());
        challenge.setSelectedAt(Instant.now());
        challenge.setStatus(MfaChallengeStatus.PENDING_VERIFICATION);
        challenge.setVerificationCodeHash(null);
        challenge.setVerificationCodeExpiresAt(null);
        
        handler.dispatch(challenge, user, method);
        mfaChallengeRepository.save(challenge);
        return toResponse(challenge, user, method, normalizedToken);
    }
    
    @Transactional
    public AuthResponse verify(MfaVerifyChallengeRequest request) {
        String normalizedToken = request.challengeToken().trim();
        MfaChallenge challenge = requireMutableActiveChallenge(normalizedToken);
        User user = requireUser(challenge.getUserId());
        userAccessValidator.validate(user);
        UserMfaMethod method = requireAvailableMethod(challenge, request.method());
        MfaMethodHandler handler = mfaMethodRegistry.getRequired(request.method());
        
        challenge.setSelectedMethod(request.method());
        challenge.setSelectedAt(challenge.getSelectedAt() == null ? Instant.now() : challenge.getSelectedAt());
        challenge.setStatus(MfaChallengeStatus.PENDING_VERIFICATION);
        
        try {
            handler.verify(challenge, user, method, request.code());
        } catch (MfaVerificationFailedException exception) {
            challenge.setVerificationAttempts(challenge.getVerificationAttempts() + 1);
            if (challenge.getVerificationAttempts() >= authProperties.getMfa().getMaxVerificationAttempts()) {
                challenge.setStatus(MfaChallengeStatus.DENIED);
                challenge.setCompletedAt(Instant.now());
            }
            mfaChallengeRepository.save(challenge);
            throw exception;
        }
        
        challenge.setStatus(MfaChallengeStatus.APPROVED);
        challenge.setCompletedAt(Instant.now());
        mfaChallengeRepository.save(challenge);
        
        TokenService.CreatedRefreshToken createdRefreshToken = tokenService.createRefreshToken(user.getId(), true, request.method());
        return AuthResponse.authenticated(
                jwtService.generateAccessToken(user, true, request.method()),
                createdRefreshToken.rawToken(),
                userAuthMapper.toResponse(user)
        );
    }
    
    @Transactional
    public MfaChallengeResponse getStatus(String challengeToken) {
        MfaChallenge challenge = requireChallenge(challengeToken.trim());
        User user = requireUser(challenge.getUserId());
        
        if (challenge.getStatus() != MfaChallengeStatus.APPROVED
                && challenge.getStatus() != MfaChallengeStatus.DENIED
                && challenge.getStatus() != MfaChallengeStatus.CANCELLED
                && challenge.getExpiresAt().isBefore(Instant.now())) {
            challenge.setStatus(MfaChallengeStatus.EXPIRED);
            challenge.setCompletedAt(Instant.now());
            mfaChallengeRepository.save(challenge);
        }
        
        UserMfaMethod selectedMethod = challenge.getSelectedMethod() == null
                ? null
                : mfaConfigurationService.getEnabledMethods(challenge.getUserId()).stream()
                        .filter(method -> method.getMethodType() == challenge.getSelectedMethod())
                        .findFirst()
                        .orElse(null);
        
        return toResponse(challenge, user, selectedMethod, challengeToken.trim());
    }
    
    private MfaChallenge requireMutableActiveChallenge(String rawChallengeToken) {
        MfaChallenge challenge = requireChallenge(rawChallengeToken);
        
        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            challenge.setStatus(MfaChallengeStatus.EXPIRED);
            challenge.setCompletedAt(Instant.now());
            mfaChallengeRepository.save(challenge);
            throw new MfaChallengeExpiredException();
        }
        
        if (challenge.getStatus() == MfaChallengeStatus.APPROVED
                || challenge.getStatus() == MfaChallengeStatus.DENIED
                || challenge.getStatus() == MfaChallengeStatus.EXPIRED
                || challenge.getStatus() == MfaChallengeStatus.CANCELLED) {
            throw new MfaChallengeResolvedException();
        }
        
        return challenge;
    }
    
    private MfaChallenge requireChallenge(String rawChallengeToken) {
        return mfaChallengeRepository.findByTokenHash(tokenHashService.hash(rawChallengeToken))
                .orElseThrow(MfaChallengeNotFoundException::new);
    }
    
    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
    
    private UserMfaMethod requireAvailableMethod(MfaChallenge challenge, MfaMethodType methodType) {
        if (!availableMethods(challenge).contains(methodType)) {
            throw new MfaMethodUnavailableException(methodType);
        }
        return mfaConfigurationService.requireEnabledMethod(challenge.getUserId(), methodType);
    }
    
    private List<MfaMethodType> availableMethods(MfaChallenge challenge) {
        if (challenge.getAvailableMethodsCsv() == null || challenge.getAvailableMethodsCsv().isBlank()) {
            return List.of();
        }
        return Arrays.stream(challenge.getAvailableMethodsCsv().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(MfaMethodType::valueOf)
                .sorted()
                .toList();
    }
    
    private MfaChallengeResponse toResponse(
            MfaChallenge challenge,
            User user,
            UserMfaMethod method,
            String rawChallengeToken
    ) {
        String deliveryHint = null;
        if (method != null) {
            deliveryHint = mfaMethodRegistry.getRequired(method.getMethodType()).deliveryHint(user, method);
        }
        
        return new MfaChallengeResponse(
                rawChallengeToken,
                challenge.getStatus(),
                availableMethods(challenge),
                challenge.getSelectedMethod(),
                challenge.getExpiresAt(),
                challenge.getVerificationCodeExpiresAt(),
                deliveryHint
        );
    }
    
    private String generateChallengeToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

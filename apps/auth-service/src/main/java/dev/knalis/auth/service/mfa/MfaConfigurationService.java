package dev.knalis.auth.service.mfa;

import dev.knalis.auth.dto.response.MfaMethodResponse;
import dev.knalis.auth.dto.response.MfaMethodsResponse;
import dev.knalis.auth.dto.response.TotpSetupResponse;
import dev.knalis.auth.entity.User;
import dev.knalis.auth.exception.InvalidCurrentPasswordException;
import dev.knalis.auth.exception.MfaMethodAlreadyEnabledException;
import dev.knalis.auth.exception.MfaMethodNotConfiguredException;
import dev.knalis.auth.exception.MfaMethodUnavailableException;
import dev.knalis.auth.exception.MfaVerificationFailedException;
import dev.knalis.auth.exception.UserNotFoundException;
import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.auth.mfa.entity.UserMfaMethod;
import dev.knalis.auth.mfa.repository.UserMfaMethodRepository;
import dev.knalis.auth.repository.UserRepository;
import dev.knalis.auth.service.token.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.knalis.auth.config.CacheConfig.MFA_ENABLED_FLAGS_CACHE;
import static dev.knalis.auth.config.CacheConfig.MFA_ENABLED_METHODS_CACHE;

@Service
@RequiredArgsConstructor
public class MfaConfigurationService {
    
    private final UserRepository userRepository;
    private final UserMfaMethodRepository userMfaMethodRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final MfaSecretEncryptionService mfaSecretEncryptionService;
    private final MfaMethodRegistry mfaMethodRegistry;
    private final TokenService tokenService;
    
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = MFA_ENABLED_FLAGS_CACHE, key = "#userId", sync = true)
    public boolean isMfaEnabledForUser(UUID userId) {
        return !userMfaMethodRepository.findAllByUserIdAndEnabledTrueOrderByMethodTypeAsc(userId).isEmpty();
    }
    
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = MFA_ENABLED_METHODS_CACHE, key = "#userId", sync = true)
    public List<UserMfaMethod> getEnabledMethods(UUID userId) {
        return userMfaMethodRepository.findAllByUserIdAndEnabledTrueOrderByMethodTypeAsc(userId);
    }
    
    @Transactional(readOnly = true)
    public UserMfaMethod requireEnabledMethod(UUID userId, MfaMethodType methodType) {
        return userMfaMethodRepository.findByUserIdAndMethodType(userId, methodType)
                .filter(UserMfaMethod::isEnabled)
                .orElseThrow(() -> new MfaMethodUnavailableException(methodType));
    }
    
    @Transactional(readOnly = true)
    public MfaMethodsResponse getMethods(UUID userId) {
        Map<MfaMethodType, UserMfaMethod> methodsByType = new EnumMap<>(MfaMethodType.class);
        for (UserMfaMethod method : userMfaMethodRepository.findAllByUserIdOrderByMethodTypeAsc(userId)) {
            methodsByType.put(method.getMethodType(), method);
        }
        
        List<MfaMethodResponse> methods = mfaMethodRegistry.supportedHandlers().stream()
                .sorted(Comparator.comparingInt(handler -> handler.getMethodType().ordinal()))
                .map(handler -> toMethodResponse(methodsByType.get(handler.getMethodType()), handler))
                .toList();
        
        return new MfaMethodsResponse(methods);
    }
    
    @Transactional
    public TotpSetupResponse setupTotp(UUID userId, String currentPassword) {
        User user = requireUser(userId);
        verifyCurrentPassword(user, currentPassword);
        
        UserMfaMethod existingMethod = userMfaMethodRepository.findByUserIdAndMethodType(userId, MfaMethodType.TOTP).orElse(null);
        if (existingMethod != null && existingMethod.isEnabled()) {
            throw new MfaMethodAlreadyEnabledException(MfaMethodType.TOTP);
        }
        
        String secret = totpService.generateSecret();
        UserMfaMethod method = existingMethod != null ? existingMethod : newMethod(userId, MfaMethodType.TOTP);
        method.setSecretEncrypted(mfaSecretEncryptionService.encrypt(secret));
        method.setMetadataJson(null);
        method.setEnabled(false);
        method.setPreferred(false);
        method.setDisabledAt(null);
        userMfaMethodRepository.save(method);
        
        return new TotpSetupResponse(secret, totpService.buildOtpauthUri(user.getUsername(), secret));
    }
    
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = MFA_ENABLED_FLAGS_CACHE, key = "#userId"),
            @CacheEvict(cacheNames = MFA_ENABLED_METHODS_CACHE, key = "#userId")
    })
    public MfaMethodsResponse confirmTotp(UUID userId, String currentPassword, String code) {
        User user = requireUser(userId);
        verifyCurrentPassword(user, currentPassword);
        
        UserMfaMethod method = userMfaMethodRepository.findByUserIdAndMethodType(userId, MfaMethodType.TOTP)
                .orElseThrow(() -> new MfaMethodNotConfiguredException(MfaMethodType.TOTP));
        
        String secret = mfaSecretEncryptionService.decrypt(method.getSecretEncrypted());
        if (!totpService.verifyCode(secret, code.trim())) {
            throw new MfaVerificationFailedException();
        }
        
        method.setEnabled(true);
        method.setEnabledAt(Instant.now());
        method.setDisabledAt(null);
        if (getEnabledMethods(userId).stream().noneMatch(UserMfaMethod::isPreferred)) {
            method.setPreferred(true);
        }
        userMfaMethodRepository.save(method);
        tokenService.revokeAllRefreshTokens(userId);
        return getMethods(userId);
    }
    
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = MFA_ENABLED_FLAGS_CACHE, key = "#userId"),
            @CacheEvict(cacheNames = MFA_ENABLED_METHODS_CACHE, key = "#userId")
    })
    public MfaMethodsResponse disableMethod(UUID userId, MfaMethodType methodType, String currentPassword) {
        User user = requireUser(userId);
        verifyCurrentPassword(user, currentPassword);
        
        UserMfaMethod method = userMfaMethodRepository.findByUserIdAndMethodType(userId, methodType)
                .orElseThrow(() -> new MfaMethodNotConfiguredException(methodType));
        method.setEnabled(false);
        method.setPreferred(false);
        method.setDisabledAt(Instant.now());
        userMfaMethodRepository.save(method);
        
        List<UserMfaMethod> enabledMethods = getEnabledMethods(userId);
        if (enabledMethods.stream().noneMatch(UserMfaMethod::isPreferred)) {
            enabledMethods.stream().findFirst().ifPresent(preferredMethod -> {
                preferredMethod.setPreferred(true);
                userMfaMethodRepository.save(preferredMethod);
            });
        }
        
        tokenService.revokeAllRefreshTokens(userId);
        return getMethods(userId);
    }
    
    private MfaMethodResponse toMethodResponse(UserMfaMethod method, MfaMethodHandler handler) {
        boolean pendingSetup = method != null
                && method.getMethodType() == MfaMethodType.TOTP
                && !method.isEnabled()
                && method.getSecretEncrypted() != null;
        
        return new MfaMethodResponse(
                handler.getMethodType(),
                method != null && method.isEnabled(),
                method != null && method.isPreferred(),
                pendingSetup,
                handler.requiresDispatch()
        );
    }
    
    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
    
    private void verifyCurrentPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
    }
    
    private UserMfaMethod newMethod(UUID userId, MfaMethodType methodType) {
        UserMfaMethod method = new UserMfaMethod();
        method.setUserId(userId);
        method.setMethodType(methodType);
        method.setEnabled(false);
        method.setPreferred(false);
        return method;
    }
}

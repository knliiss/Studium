package dev.knalis.auth.mfa.repository;

import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.auth.mfa.entity.UserMfaMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMfaMethodRepository extends JpaRepository<UserMfaMethod, UUID> {
    
    List<UserMfaMethod> findAllByUserIdOrderByMethodTypeAsc(UUID userId);
    
    List<UserMfaMethod> findAllByUserIdAndEnabledTrueOrderByMethodTypeAsc(UUID userId);
    
    Optional<UserMfaMethod> findByUserIdAndMethodType(UUID userId, MfaMethodType methodType);
}

package dev.knalis.auth.service.common;

import dev.knalis.auth.config.AuthProperties;
import dev.knalis.auth.factory.user.UserFactory;
import dev.knalis.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerBootstrapService implements ApplicationRunner {
    
    private final AuthProperties authProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserFactory userFactory;
    
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!authProperties.getOwner().isSeedEnabled()) {
            log.info("Owner bootstrap is disabled.");
            return;
        }
        
        String username = authProperties.getOwner().getUsername();
        String email = authProperties.getOwner().getEmail();
        String password = authProperties.getOwner().getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("Owner bootstrap is enabled but owner credentials are not fully configured");
        }
        
        boolean exists = userRepository.existsByUsernameIgnoreCase(username)
                || userRepository.existsByEmailIgnoreCase(email);
        
        if (exists) {
            log.info("Owner already exists. Bootstrap skipped.");
            return;
        }
        
        var owner = userFactory.newBootstrapOwner(
                username,
                email,
                passwordEncoder.encode(password)
        );
        
        userRepository.save(owner);
        log.info("Initial OWNER account has been created with username={}", username);
    }
}

package dev.knalis.auth.repository;

import dev.knalis.auth.entity.Role;
import dev.knalis.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    
    Optional<User> findByUsernameIgnoreCase(String username);
    
    Optional<User> findByEmailIgnoreCase(String email);
    
    boolean existsByUsernameIgnoreCase(String username);
    
    boolean existsByEmailIgnoreCase(String email);
    
    long countByRolesContaining(Role role);
    
}
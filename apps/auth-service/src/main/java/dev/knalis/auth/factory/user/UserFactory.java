package dev.knalis.auth.factory.user;

import dev.knalis.auth.entity.Role;
import dev.knalis.auth.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class UserFactory {
    
    public User newRegisteredUser(String username, String email, String passwordHash) {
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(passwordHash);
        user.setRoles(Set.of(Role.USER));
        user.setForcePasswordChange(false);
        return user;
    }
    
    public User newBootstrapOwner(String username, String email, String passwordHash) {
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(passwordHash);
        user.setRoles(Set.of(Role.OWNER, Role.ADMIN, Role.USER));
        user.setForcePasswordChange(true);
        return user;
    }
}
package dev.knalis.auth.service.auth;

import dev.knalis.auth.dto.response.UserSummaryResponse;
import dev.knalis.auth.entity.User;
import dev.knalis.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> lookupUsers(List<UUID> userIds) {
        List<UUID> normalizedIds = userIds == null ? List.of() : userIds.stream()
                .filter(userId -> userId != null)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> usersById = new LinkedHashMap<>();
        for (User user : userRepository.findAllById(normalizedIds)) {
            usersById.put(user.getId(), user);
        }

        return normalizedIds.stream()
                .map(usersById::get)
                .filter(user -> user != null)
                .map(this::toSummary)
                .toList();
    }

    private UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles()
        );
    }
}

package dev.knalis.gateway.client.auth.dto;

import java.util.List;
import java.util.UUID;

public record UserDirectoryLookupRequest(
        List<UUID> userIds
) {
}

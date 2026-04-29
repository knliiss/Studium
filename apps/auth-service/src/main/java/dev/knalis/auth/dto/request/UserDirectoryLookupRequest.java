package dev.knalis.auth.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UserDirectoryLookupRequest(

        @NotEmpty
        @Size(max = 100)
        List<UUID> userIds
) {
}

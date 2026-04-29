package dev.knalis.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AdminBanUserRequest(
        
        @NotBlank
        @Size(max = 500)
        String reason,
        
        Instant expiresAt
) {
}
package dev.knalis.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        
        @NotBlank
        @Size(min = 8, max = 100)
        String currentPassword,
        
        @NotBlank
        @Size(min = 8, max = 100)
        String newPassword
) {
}
package dev.knalis.auth.dto.request;

import dev.knalis.auth.mfa.entity.MfaMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisableMfaMethodRequest(
        @NotNull
        MfaMethodType method,
        @NotBlank
        @Size(min = 8, max = 100)
        String currentPassword
) {
}

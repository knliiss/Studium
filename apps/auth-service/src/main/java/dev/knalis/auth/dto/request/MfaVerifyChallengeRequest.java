package dev.knalis.auth.dto.request;

import dev.knalis.auth.mfa.entity.MfaMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MfaVerifyChallengeRequest(
        @NotBlank
        String challengeToken,
        @NotNull
        MfaMethodType method,
        @Size(max = 12)
        String code
) {
}

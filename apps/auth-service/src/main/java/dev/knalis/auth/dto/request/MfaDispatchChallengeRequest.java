package dev.knalis.auth.dto.request;

import dev.knalis.auth.mfa.entity.MfaMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MfaDispatchChallengeRequest(
        @NotBlank
        String challengeToken,
        @NotNull
        MfaMethodType method
) {
}

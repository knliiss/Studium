package dev.knalis.auth.dto.response;

import dev.knalis.auth.mfa.entity.MfaChallengeStatus;
import dev.knalis.auth.mfa.entity.MfaMethodType;

import java.time.Instant;
import java.util.List;

public record MfaChallengeResponse(
        String challengeToken,
        MfaChallengeStatus status,
        List<MfaMethodType> availableMethods,
        MfaMethodType selectedMethod,
        Instant expiresAt,
        Instant codeExpiresAt,
        String deliveryHint
) {
}

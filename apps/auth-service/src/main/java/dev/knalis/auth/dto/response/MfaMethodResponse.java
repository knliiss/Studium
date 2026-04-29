package dev.knalis.auth.dto.response;

import dev.knalis.auth.mfa.entity.MfaMethodType;

public record MfaMethodResponse(
        MfaMethodType type,
        boolean enabled,
        boolean preferred,
        boolean pendingSetup,
        boolean dispatchRequired
) {
}

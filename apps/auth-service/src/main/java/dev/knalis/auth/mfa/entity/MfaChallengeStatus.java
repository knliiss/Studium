package dev.knalis.auth.mfa.entity;

public enum MfaChallengeStatus {
    PENDING_SELECTION,
    PENDING_VERIFICATION,
    APPROVED,
    DENIED,
    EXPIRED,
    CANCELLED
}

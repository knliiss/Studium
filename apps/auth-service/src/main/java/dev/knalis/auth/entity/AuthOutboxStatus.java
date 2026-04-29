package dev.knalis.auth.entity;

public enum AuthOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    PUBLISHED,
    FAILED
}

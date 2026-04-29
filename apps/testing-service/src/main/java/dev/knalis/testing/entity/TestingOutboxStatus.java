package dev.knalis.testing.entity;

public enum TestingOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    PUBLISHED,
    FAILED
}

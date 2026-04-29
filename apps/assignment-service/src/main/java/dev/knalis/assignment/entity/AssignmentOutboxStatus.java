package dev.knalis.assignment.entity;

public enum AssignmentOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    PUBLISHED,
    FAILED
}

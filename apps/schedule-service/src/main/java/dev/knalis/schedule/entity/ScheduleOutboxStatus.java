package dev.knalis.schedule.entity;

public enum ScheduleOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    PUBLISHED,
    FAILED
}

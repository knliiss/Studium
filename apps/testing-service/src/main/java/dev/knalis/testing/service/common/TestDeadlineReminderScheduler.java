package dev.knalis.testing.service.common;

import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.client.education.dto.GroupStudentUserResponse;
import dev.knalis.testing.client.education.dto.SubjectResponse;
import dev.knalis.testing.client.notification.NotificationServiceClient;
import dev.knalis.testing.config.TestingReminderProperties;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.repository.TestRepository;
import dev.knalis.testing.repository.TestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestDeadlineReminderScheduler {

    private static final List<Duration> REMINDER_OFFSETS = List.of(Duration.ofHours(24), Duration.ofHours(2));

    private final TestRepository testRepository;
    private final TestResultRepository testResultRepository;
    private final EducationServiceClient educationServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final TestingReminderProperties testingReminderProperties;

    @Scheduled(fixedDelayString = "${app.testing.reminders.check-interval:30m}")
    public void sendDeadlineReminders() {
        if (!testingReminderProperties.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        Duration lookback = testingReminderProperties.getLookback();

        for (Duration reminderOffset : REMINDER_OFFSETS) {
            Instant deadlineWindowStart = now.minus(lookback).plus(reminderOffset);
            Instant deadlineWindowEnd = now.plus(reminderOffset);
            List<Test> tests = testRepository.findAllByStatusAndAvailableUntilBetween(
                    TestStatus.PUBLISHED,
                    deadlineWindowStart,
                    deadlineWindowEnd
            );
            for (Test test : tests) {
                sendReminderForTest(test, reminderOffset);
            }
        }
    }

    private void sendReminderForTest(Test test, Duration reminderOffset) {
        if (test.getAvailableUntil() == null) {
            return;
        }

        SubjectResponse subject = educationServiceClient.getSubject(
                educationServiceClient.getTopic(test.getTopicId()).subjectId()
        );
        Instant reminderAt = test.getAvailableUntil().minus(reminderOffset);
        for (GroupStudentUserResponse student : educationServiceClient.getGroupStudents(subject.groupId())) {
            if (testResultRepository.existsByTestIdAndUserId(test.getId(), student.userId())) {
                continue;
            }
            notificationServiceClient.createTestDeadlineReminder(
                    student.userId(),
                    test.getId(),
                    test.getTitle(),
                    test.getAvailableUntil(),
                    reminderAt
            );
        }
        log.debug("Processed test reminders for testId={}, offset={}", test.getId(), reminderOffset);
    }
}

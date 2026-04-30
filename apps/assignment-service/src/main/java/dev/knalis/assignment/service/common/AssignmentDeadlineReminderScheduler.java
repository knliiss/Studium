package dev.knalis.assignment.service.common;

import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.education.dto.GroupStudentUserResponse;
import dev.knalis.assignment.client.education.dto.SubjectResponse;
import dev.knalis.assignment.client.notification.NotificationServiceClient;
import dev.knalis.assignment.config.AssignmentReminderProperties;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
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
public class AssignmentDeadlineReminderScheduler {

    private static final List<Duration> REMINDER_OFFSETS = List.of(Duration.ofHours(24), Duration.ofHours(2));

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final EducationServiceClient educationServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final AssignmentReminderProperties assignmentReminderProperties;

    @Scheduled(fixedDelayString = "${app.assignment.reminders.check-interval:30m}")
    public void sendDeadlineReminders() {
        if (!assignmentReminderProperties.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        Duration lookback = assignmentReminderProperties.getLookback();

        for (Duration reminderOffset : REMINDER_OFFSETS) {
            Instant deadlineWindowStart = now.minus(lookback).plus(reminderOffset);
            Instant deadlineWindowEnd = now.plus(reminderOffset);
            List<Assignment> assignments = assignmentRepository.findAllByStatusAndDeadlineBetween(
                    AssignmentStatus.PUBLISHED,
                    deadlineWindowStart,
                    deadlineWindowEnd
            );
            for (Assignment assignment : assignments) {
                sendReminderForAssignment(assignment, reminderOffset);
            }
        }
    }

    private void sendReminderForAssignment(Assignment assignment, Duration reminderOffset) {
        SubjectResponse subject = educationServiceClient.getSubject(
                educationServiceClient.getTopic(assignment.getTopicId()).subjectId()
        );
        if (subject.groupId() == null) {
            log.debug("Skipping assignment reminder for unbound subjectId={}", subject.id());
            return;
        }
        Instant reminderAt = assignment.getDeadline().minus(reminderOffset);
        for (GroupStudentUserResponse student : educationServiceClient.getGroupStudents(subject.groupId())) {
            if (submissionRepository.existsByAssignmentIdAndUserId(assignment.getId(), student.userId())) {
                continue;
            }
            notificationServiceClient.createAssignmentDeadlineReminder(
                    student.userId(),
                    assignment.getId(),
                    assignment.getTitle(),
                    assignment.getDeadline(),
                    reminderAt
            );
        }
        log.debug("Processed assignment reminders for assignmentId={}, offset={}", assignment.getId(), reminderOffset);
    }
}

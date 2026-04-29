package dev.knalis.notification.messaging;

import dev.knalis.contracts.event.AssignmentCreatedEventV1;
import dev.knalis.contracts.event.GradeAssignedEventV1;
import dev.knalis.contracts.event.ScheduleExtraLessonCreatedEventV1;
import dev.knalis.contracts.event.ScheduleLessonCancelledEventV1;
import dev.knalis.contracts.event.ScheduleLessonFormatV1;
import dev.knalis.contracts.event.ScheduleLessonReplacedEventV1;
import dev.knalis.contracts.event.ScheduleLessonTypeV1;
import dev.knalis.notification.client.education.EducationServiceClient;
import dev.knalis.notification.client.education.dto.GroupStudentUserResponse;
import dev.knalis.notification.client.education.dto.SubjectResponse;
import dev.knalis.notification.client.education.dto.TopicResponse;
import dev.knalis.notification.entity.Notification;
import dev.knalis.notification.entity.NotificationType;
import dev.knalis.notification.repository.NotificationRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(topics = {
        "schedule.override-created.v1",
        "schedule.lesson-cancelled.v1",
        "schedule.lesson-replaced.v1",
        "schedule.extra-lesson-created.v1",
        "assignment.assignment-created.v1",
        "assignment.assignment-updated.v1",
        "assignment.grade-assigned.v1",
        "testing.test-published.v1"
})
class AcademicNotificationEventsIntegrationTest {
    
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @MockitoBean
    private EducationServiceClient educationServiceClient;
    
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("app.notification.realtime.redis-fanout-enabled", () -> false);
        registry.add("app.notification.internal.shared-secret", () -> "test-notification-secret");
        registry.add("app.notification.education-service.base-url", () -> "http://localhost:8085");
        registry.add("app.notification.education-service.shared-secret", () -> "test-education-secret");
        registry.add("app.notification.jwt.public-key-path", () ->
                Path.of("infra", "keys", "public.pem").toAbsolutePath().toUri().toString());
    }
    
    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        Mockito.reset(educationServiceClient);
    }
    
    @Test
    void scheduleCancelEventCreatesNotificationsForStudentsAndTeacher() {
        UUID groupId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentOne = UUID.randomUUID();
        UUID studentTwo = UUID.randomUUID();
        
        when(educationServiceClient.getGroupStudents(groupId)).thenReturn(List.of(
                new GroupStudentUserResponse(studentOne),
                new GroupStudentUserResponse(studentTwo)
        ));
        
        ScheduleLessonCancelledEventV1 event = new ScheduleLessonCancelledEventV1(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 7),
                groupId,
                UUID.randomUUID(),
                teacherId,
                UUID.randomUUID(),
                ScheduleLessonTypeV1.LECTURE,
                ScheduleLessonFormatV1.OFFLINE,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "Teacher unavailable"
        );
        
        kafkaTemplate().send("schedule.lesson-cancelled.v1", event.overrideId().toString(), event);
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Notification> notifications = notificationRepository.findAll();
                    assertEquals(3, notifications.size());
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getType() == NotificationType.SCHEDULE_LESSON_CANCELLED));
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getBody().contains("Lecture")));
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getPayloadJson().contains("\"lessonType\":\"LECTURE\"")));
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getPayloadJson().contains("\"lessonTypeDisplayName\":\"Lecture\"")));
                    assertEquals(
                            Set.of(studentOne, studentTwo, teacherId),
                            notifications.stream().map(Notification::getUserId).collect(Collectors.toSet())
                    );
                });
    }
    
    @Test
    void scheduleReplaceEventCreatesNotifications() {
        UUID groupId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentOne = UUID.randomUUID();
        UUID studentTwo = UUID.randomUUID();
        UUID oldRoomId = UUID.randomUUID();
        UUID newRoomId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        
        when(educationServiceClient.getGroupStudents(groupId)).thenReturn(List.of(
                new GroupStudentUserResponse(studentOne),
                new GroupStudentUserResponse(studentTwo)
        ));
        
        ScheduleLessonReplacedEventV1 event = new ScheduleLessonReplacedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 8),
                teacherId,
                teacherId,
                oldRoomId,
                newRoomId,
                slotId,
                slotId,
                ScheduleLessonTypeV1.PRACTICAL,
                ScheduleLessonFormatV1.OFFLINE,
                ScheduleLessonFormatV1.OFFLINE,
                null,
                groupId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        
        kafkaTemplate().send("schedule.lesson-replaced.v1", event.overrideId().toString(), event);
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Notification> notifications = notificationRepository.findAll();
                    assertEquals(3, notifications.size());
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getType() == NotificationType.SCHEDULE_ROOM_CHANGED));
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getBody().contains("Practical")));
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getPayloadJson().contains("\"lessonType\":\"PRACTICAL\"")));
                });
    }
    
    @Test
    void extraLessonEventCreatesNotifications() {
        UUID groupId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentOne = UUID.randomUUID();
        UUID studentTwo = UUID.randomUUID();
        
        when(educationServiceClient.getGroupStudents(groupId)).thenReturn(List.of(
                new GroupStudentUserResponse(studentOne),
                new GroupStudentUserResponse(studentTwo)
        ));
        
        ScheduleExtraLessonCreatedEventV1 event = new ScheduleExtraLessonCreatedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 9),
                groupId,
                UUID.randomUUID(),
                teacherId,
                UUID.randomUUID(),
                ScheduleLessonTypeV1.LABORATORY,
                ScheduleLessonFormatV1.ONLINE,
                null,
                "https://meet.example.com/extra",
                "Additional practice",
                UUID.randomUUID()
        );
        
        kafkaTemplate().send("schedule.extra-lesson-created.v1", event.overrideId().toString(), event);
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Notification> notifications = notificationRepository.findAll();
                    assertEquals(3, notifications.size());
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getType() == NotificationType.SCHEDULE_EXTRA_LESSON));
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getBody().contains("Laboratory")));
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getPayloadJson().contains("\"lessonTypeDisplayName\":\"Laboratory\"")));
                });
    }
    
    @Test
    void gradeAssignedEventNotifiesOnlyStudent() {
        UUID studentUserId = UUID.randomUUID();
        GradeAssignedEventV1 event = new GradeAssignedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                studentUserId,
                91,
                "Good work",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        
        kafkaTemplate().send("assignment.grade-assigned.v1", event.gradeId().toString(), event);
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Notification> notifications = notificationRepository.findAll();
                    assertEquals(1, notifications.size());
                    assertEquals(studentUserId, notifications.getFirst().getUserId());
                    assertEquals(NotificationType.GRADE_ASSIGNED, notifications.getFirst().getType());
                });
    }
    
    @Test
    void assignmentCreatedEventNotifiesAffectedStudents() {
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID studentOne = UUID.randomUUID();
        UUID studentTwo = UUID.randomUUID();
        
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId,
                subjectId,
                "Loops",
                1,
                Instant.now(),
                Instant.now()
        ));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(new SubjectResponse(
                subjectId,
                "Programming",
                groupId,
                "desc",
                Instant.now(),
                Instant.now()
        ));
        when(educationServiceClient.getGroupStudents(groupId)).thenReturn(List.of(
                new GroupStudentUserResponse(studentOne),
                new GroupStudentUserResponse(studentTwo)
        ));
        
        AssignmentCreatedEventV1 event = new AssignmentCreatedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                topicId,
                "Lab 1",
                Instant.parse("2026-09-20T12:00:00Z"),
                UUID.randomUUID()
        );
        
        kafkaTemplate().send("assignment.assignment-created.v1", event.assignmentId().toString(), event);
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Notification> notifications = notificationRepository.findAll();
                    assertEquals(2, notifications.size());
                    assertTrue(notifications.stream().allMatch(notification ->
                            notification.getType() == NotificationType.ASSIGNMENT_CREATED));
                });
    }
    
    @Test
    void duplicateEventDoesNotCreateDuplicateNotifications() {
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID studentOne = UUID.randomUUID();
        UUID studentTwo = UUID.randomUUID();
        
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId,
                subjectId,
                "Recursion",
                1,
                Instant.now(),
                Instant.now()
        ));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(new SubjectResponse(
                subjectId,
                "Algorithms",
                groupId,
                "desc",
                Instant.now(),
                Instant.now()
        ));
        when(educationServiceClient.getGroupStudents(groupId)).thenReturn(List.of(
                new GroupStudentUserResponse(studentOne),
                new GroupStudentUserResponse(studentTwo)
        ));
        
        AssignmentCreatedEventV1 event = new AssignmentCreatedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                topicId,
                "Lab 2",
                Instant.parse("2026-09-21T12:00:00Z"),
                UUID.randomUUID()
        );
        
        kafkaTemplate().send("assignment.assignment-created.v1", event.assignmentId().toString(), event);
        kafkaTemplate().send("assignment.assignment-created.v1", event.assignmentId().toString(), event);
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(2, notificationRepository.count()));
    }
    
    private KafkaTemplate<String, Object> kafkaTemplate() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                System.getProperty("spring.embedded.kafka.brokers"));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }
}

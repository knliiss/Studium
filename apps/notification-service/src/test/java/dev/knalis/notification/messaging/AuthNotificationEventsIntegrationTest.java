package dev.knalis.notification.messaging;

import dev.knalis.contracts.event.UserRegisteredEvent;
import dev.knalis.notification.entity.Notification;
import dev.knalis.notification.entity.NotificationType;
import dev.knalis.notification.repository.NotificationRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(topics = {
        "auth.user-registered.v1",
        "auth.user-email-changed.v1",
        "auth.user-username-changed.v1",
        "auth.user-banned.v1",
        "auth.user-unbanned.v1"
})
class AuthNotificationEventsIntegrationTest {
    
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("app.notification.realtime.redis-fanout-enabled", () -> false);
        registry.add("app.notification.internal.shared-secret", () -> "test-notification-secret");
        registry.add("app.notification.jwt.public-key-path", () ->
                Path.of("infra", "keys", "public.pem").toAbsolutePath().toUri().toString());
    }
    
    @Test
    void userRegisteredEventCreatesPersistentNotification() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(),
                userId,
                "new-user",
                "new-user@example.com",
                Instant.now()
        );
        
        kafkaTemplate().send("auth.user-registered.v1", userId.toString(), event);
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Notification notification = notificationRepository.findBySourceEventId(event.eventId()).orElseThrow();
                    assertEquals(userId, notification.getUserId());
                    assertEquals(NotificationType.WELCOME, notification.getType());
                });
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

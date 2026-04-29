package dev.knalis.profile.messaging;

import dev.knalis.contracts.event.UserEmailChangedEvent;
import dev.knalis.contracts.event.UserRegisteredEvent;
import dev.knalis.contracts.event.UserUsernameChangedEvent;
import dev.knalis.profile.entity.UserProfile;
import dev.knalis.profile.repository.UserProfileRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
        "auth.user-username-changed.v1"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserProfileKafkaIntegrationTest {
    
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    
    private static KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    private UserProfileRepository userProfileRepository;
    
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", () ->
                System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () ->
                "dev.knalis.contracts.event");
    }
    
    @BeforeAll
    void setUpKafkaTemplate() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                System.getProperty("spring.embedded.kafka.brokers"));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class);
        DefaultKafkaProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(properties);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }
    
    @AfterAll
    void tearDownKafkaTemplate() {
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
    }
    
    @Test
    void userRegisteredEventCreatesProfileAndSyncEventsUpdateIt() {
        UUID userId = UUID.randomUUID();
        
        kafkaTemplate.send("auth.user-registered.v1", userId.toString(), new UserRegisteredEvent(
                UUID.randomUUID(),
                userId,
                "initial-user",
                "initial@example.com",
                Instant.now()
        ));
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    UserProfile profile = userProfileRepository.findByUserId(userId).orElseThrow();
                    assertEquals("initial-user", profile.getUsername());
                    assertEquals("initial@example.com", profile.getEmail());
                });
        
        kafkaTemplate.send("auth.user-email-changed.v1", userId.toString(), new UserEmailChangedEvent(
                UUID.randomUUID(),
                userId,
                "initial@example.com",
                "updated@example.com",
                Instant.now()
        ));
        
        kafkaTemplate.send("auth.user-username-changed.v1", userId.toString(), new UserUsernameChangedEvent(
                UUID.randomUUID(),
                userId,
                "initial-user",
                "updated-user",
                Instant.now()
        ));
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    UserProfile profile = userProfileRepository.findByUserId(userId).orElseThrow();
                    assertEquals("updated-user", profile.getUsername());
                    assertEquals("updated@example.com", profile.getEmail());
                    assertEquals("updated-user", profile.getDisplayName());
                });
    }
}

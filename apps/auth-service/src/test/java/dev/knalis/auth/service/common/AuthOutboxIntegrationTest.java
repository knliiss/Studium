package dev.knalis.auth.service.common;

import dev.knalis.auth.entity.AuthOutboxStatus;
import dev.knalis.auth.repository.AuthOutboxRepository;
import dev.knalis.contracts.event.UserRegisteredEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(topics = {"auth.user-registered.v1"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthOutboxIntegrationTest {
    
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    
    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;
    
    @Autowired
    private AuthEventPublisher authEventPublisher;
    
    @Autowired
    private AuthOutboxRepository authOutboxRepository;
    
    private Consumer<String, UserRegisteredEvent> consumer;
    
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("app.kafka.outbox.publish-interval", () -> "250ms");
        registry.add("app.kafka.outbox.cleanup-interval", () -> "1d");
        registry.add("app.auth.mfa.encryption-key", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    
    @BeforeAll
    void setUpConsumer() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "auth-outbox-test",
                "true",
                embeddedKafkaBroker
        );
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "dev.knalis.contracts.event");
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(UserRegisteredEvent.class, false)
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAllEmbeddedTopics(consumer);
    }
    
    @AfterAll
    void tearDownConsumer() {
        if (consumer != null) {
            consumer.close();
        }
    }
    
    @Test
    void outboxPublishesEventToKafkaAndMarksRowPublished() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "outbox-user",
                "outbox@example.com",
                Instant.now()
        );
        
        authEventPublisher.publishUserRegistered(event);
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertTrue(authOutboxRepository.findAll().stream()
                                .anyMatch(outboxEvent -> outboxEvent.getStatus() == AuthOutboxStatus.PUBLISHED)));
        
        ConsumerRecord<String, UserRegisteredEvent> record = KafkaTestUtils.getSingleRecord(
                consumer,
                "auth.user-registered.v1",
                Duration.ofSeconds(10)
        );
        
        assertEquals(event.userId().toString(), record.key());
        assertEquals(event.userId(), record.value().userId());
        assertEquals(event.email(), record.value().email());
    }
}

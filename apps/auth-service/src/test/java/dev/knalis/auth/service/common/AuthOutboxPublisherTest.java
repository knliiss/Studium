package dev.knalis.auth.service.common;

import dev.knalis.auth.config.AuthOutboxProperties;
import dev.knalis.auth.entity.AuthOutboxEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthOutboxPublisherTest {
    
    @Mock
    private AuthOutboxService authOutboxService;
    
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    private AuthOutboxPublisher authOutboxPublisher;
    
    @BeforeEach
    void setUp() {
        AuthOutboxProperties properties = new AuthOutboxProperties();
        properties.setSendTimeout(Duration.ofSeconds(1));
        authOutboxPublisher = new AuthOutboxPublisher(
                authOutboxService,
                properties,
                kafkaTemplate,
                new SimpleMeterRegistry()
        );
    }
    
    @Test
    void publishClaimedEventMarksEventPublishedOnKafkaAck() {
        AuthOutboxEvent event = new AuthOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setTopic("auth.user-registered.v1");
        event.setMessageKey("user-id");
        event.setEventType("UserRegisteredEvent");
        event.setPayloadType(String.class.getName());
        event.setPayloadJson("\"payload\"");
        event.setNextAttemptAt(Instant.now());
        
        when(authOutboxService.deserialize(event)).thenReturn("payload");
        when(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), "payload"))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(
                        new ProducerRecord<>(event.getTopic(), event.getMessageKey(), "payload"),
                        new RecordMetadata(new TopicPartition(event.getTopic(), 0), 0, 42, 0L, 0, 0)
                )));
        
        authOutboxPublisher.publishClaimedEvent(event);
        
        verify(authOutboxService).markPublished(event.getId(), 0, 42L);
    }
    
    @Test
    void publishClaimedEventMarksRetryOnKafkaFailure() {
        AuthOutboxEvent event = new AuthOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setTopic("auth.user-registered.v1");
        event.setMessageKey("user-id");
        event.setEventType("UserRegisteredEvent");
        event.setPayloadType(String.class.getName());
        event.setPayloadJson("\"payload\"");
        event.setNextAttemptAt(Instant.now());
        
        when(authOutboxService.deserialize(event)).thenReturn("payload");
        when(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), "payload"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));
        
        authOutboxPublisher.publishClaimedEvent(event);
        
        verify(authOutboxService).markRetry(event.getId(), "java.lang.RuntimeException: kafka down");
    }
}

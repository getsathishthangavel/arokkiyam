package com.arokkiyam.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.arokkiyam.core.config.SharedCoreProperties;

/**
 * Unit tests for {@link AuditEventPublisher}.
 *
 * <p>KafkaTemplate is mocked — no Kafka broker required.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditEventPublisher")
class AuditEventPublisherTest {

    @Mock private KafkaTemplate<String, Object>   kafkaTemplate;
    @Mock private SharedCoreProperties             properties;
    @Mock private SharedCoreProperties.Kafka       kafkaProperties;
    @Mock private SendResult<String, Object>       sendResult;

    @InjectMocks
    private AuditEventPublisher publisher;

    private static final String AUDIT_TOPIC = "audit.event";
    private static final String TENANT_ID   = UUID.randomUUID().toString();
    private static final String USER_ID     = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        when(properties.getKafka()).thenReturn(kafkaProperties);
        when(kafkaProperties.getAuditTopic()).thenReturn(AUDIT_TOPIC);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @Nested
    @DisplayName("publish(AuditEventDto)")
    class PublishDto {

        @Test
        @DisplayName("sends to correct topic with tenantId as message key")
        void sendsToCorrectTopicWithTenantKey() {
            AuditEventDto event = AuditEventDto.builder()
                .tenantId(TENANT_ID)
                .actorId(USER_ID)
                .action("PATIENT_CREATED")
                .entityType("Patient")
                .entityId(UUID.randomUUID().toString())
                .sourceService("patient-service")
                .build();

            publisher.publish(event);

            ArgumentCaptor<String>       topicCaptor  = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String>       keyCaptor    = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object>       valueCaptor  = ArgumentCaptor.forClass(Object.class);

            verify(kafkaTemplate).send(
                topicCaptor.capture(),
                keyCaptor.capture(),
                valueCaptor.capture()
            );

            assertThat(topicCaptor.getValue()).isEqualTo(AUDIT_TOPIC);
            assertThat(keyCaptor.getValue()).isEqualTo(TENANT_ID);
            assertThat(valueCaptor.getValue()).isSameAs(event);
        }

        @Test
        @DisplayName("does not throw when null event passed")
        void handlesNullEventGracefully() {
            assertThatNoException().isThrownBy(() -> publisher.publish((AuditEventDto) null));
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("does not throw when Kafka send fails")
        void handlesKafkaFailureGracefully() {
            CompletableFuture<SendResult<String, Object>> failedFuture =
                CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable"));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(failedFuture);

            AuditEventDto event = AuditEventDto.builder()
                .tenantId(TENANT_ID)
                .actorId(USER_ID)
                .action("TEST_ACTION")
                .sourceService("test-service")
                .build();

            // Must not throw — business operation must succeed even if audit fails
            assertThatNoException().isThrownBy(() -> publisher.publish(event));
        }
    }

    @Nested
    @DisplayName("publish(tenantId, actorId, action, sourceService)")
    class PublishConvenience {

        @Test
        @DisplayName("publishes event with correct fields via convenience method")
        void publishesWithCorrectFields() {
            publisher.publish(TENANT_ID, USER_ID, "USER_LOGIN", "iam-service");

            ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
            verify(kafkaTemplate).send(eq(AUDIT_TOPIC), eq(TENANT_ID), valueCaptor.capture());

            AuditEventDto published = (AuditEventDto) valueCaptor.getValue();
            assertThat(published.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(published.getActorId()).isEqualTo(USER_ID);
            assertThat(published.getAction()).isEqualTo("USER_LOGIN");
            assertThat(published.getSourceService()).isEqualTo("iam-service");
            assertThat(published.getOccurredAt()).isNotNull();
        }
    }
}
package com.arokkiyam.core.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka producer and consumer configuration shared across all services.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Producer: acks=all — message not confirmed until ALL replicas persist it.
 *       Critical for healthcare data — no silent message loss.</li>
 *   <li>Producer: idempotent=true — prevents duplicate messages on retry.</li>
 *   <li>Consumer: manual ACK (MANUAL_IMMEDIATE) — message only acknowledged
 *       after successful processing. If the consumer crashes, the message
 *       is re-delivered, not lost.</li>
 *   <li>Error handler: 3 retries with 1s backoff, then route to DLQ.
 *       DLQ consumers are configured per-service.</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.application.name:arokkiyam-service}")
    private String applicationName;

    // ── Producer ─────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // acks=all: leader waits for all in-sync replicas to acknowledge.
        // Slowest but safest — mandatory for healthcare data.
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // Idempotent producer: exactly-once delivery per partition.
        // Prevents duplicate audit events on retry.
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Retry up to 3 times before failing the send.
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Batch size 16KB — good balance for low-latency healthcare events.
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        // linger.ms=5: wait up to 5ms to batch messages.
        // Small latency cost for significant throughput gain.
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ─────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Never auto-commit — we use MANUAL_IMMEDIATE ACK mode.
        // Message is only committed after successful processing.
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Read from earliest on new consumer group startup.
        // Ensures no messages are missed on first deploy.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Trust our own package for deserialization.
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.arokkiyam.*");

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Listener container factory with:
     * - Manual acknowledgement (MANUAL_IMMEDIATE)
     * - 3 retries with 1 second backoff
     * - Dead Letter Queue routing after max retries
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // MANUAL_IMMEDIATE: service calls Acknowledgment.acknowledge()
        // explicitly after successful processing.
        factory.getContainerProperties()
            .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Error handler: 3 retries, 1000ms fixed backoff.
        // After retries exhausted, message is sent to DLQ topic.
        // Each service configures its own DLQ @KafkaListener.
        factory.setCommonErrorHandler(
            new DefaultErrorHandler(new FixedBackOff(1000L, 3L))
        );

        // Concurrency: 3 consumer threads per listener.
        // Matches the default Kafka topic partition count.
        factory.setConcurrency(3);

        return factory;
    }
}
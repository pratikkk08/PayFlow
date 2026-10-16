package com.payflow.outbox.consume;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Optional Kafka consumer wiring for domain events.
 *
 * <p>Enabled only when {@code payflow.kafka.enabled=true} and
 * {@code payflow.kafka.consumer.enabled=true}. Uses manual immediate ack,
 * bounded retries, and DLT recovery so failed records are never silently skipped.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(DomainEventConsumerProperties.class)
public class DomainEventConsumerConfig {

	private static final Logger log = LoggerFactory.getLogger(DomainEventConsumerConfig.class);

	@Bean
	@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "true")
	@ConditionalOnProperty(name = "payflow.kafka.consumer.enabled", havingValue = "true")
	ConsumerFactory<String, String> domainEventConsumerFactory(
			@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
			DomainEventConsumerProperties consumerProperties) {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProperties.getGroupId());
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.CLIENT_ID_CONFIG, consumerProperties.getIdentity());
		return new DefaultKafkaConsumerFactory<>(props);
	}

	@Bean
	@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "true")
	@ConditionalOnProperty(name = "payflow.kafka.consumer.enabled", havingValue = "true")
	ConcurrentKafkaListenerContainerFactory<String, String> domainEventKafkaListenerContainerFactory(
			ConsumerFactory<String, String> domainEventConsumerFactory,
			KafkaTemplate<String, String> kafkaTemplate,
			DomainEventConsumerProperties consumerProperties) {

		ConcurrentKafkaListenerContainerFactory<String, String> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(domainEventConsumerFactory);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
		factory.getContainerProperties().setSyncCommits(true);

		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(record, ex) -> new TopicPartition(consumerProperties.getDltTopic(), record.partition()));

		long retries = Math.max(0, consumerProperties.getMaxAttempts() - 1L);
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
				new FixedBackOff(consumerProperties.getBackoffIntervalMs(), retries));
		errorHandler.addNotRetryableExceptions(InvalidDomainEventException.class);
		errorHandler.setCommitRecovered(true);

		factory.setCommonErrorHandler(errorHandler);
		return factory;
	}

	@Bean
	@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "true")
	@ConditionalOnProperty(name = "payflow.kafka.consumer.enabled", havingValue = "true")
	ApplicationRunner domainEventConsumerEnabledNotice(DomainEventConsumerProperties props) {
		return args -> log.info(
				"Domain-event Kafka consumer enabled: topic={} group={} identity={} dlt={} "
						+ "(at-least-once; dedupe via processed_events)",
				props.getTopic(), props.getGroupId(), props.getIdentity(), props.getDltTopic());
	}
}

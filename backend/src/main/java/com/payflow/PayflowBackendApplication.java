package com.payflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Kafka autoconfiguration is on the classpath but optional at runtime:
 * set {@code payflow.kafka.enabled=true} / {@code PAYFLOW_KAFKA_ENABLED=true} and start the
 * Compose {@code kafka} profile when the outbox publisher should relay events.
 * Payment/refund APIs do not require a broker.
 */
@SpringBootApplication(exclude = {
		UserDetailsServiceAutoConfiguration.class
})
public class PayflowBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PayflowBackendApplication.class, args);
	}

}

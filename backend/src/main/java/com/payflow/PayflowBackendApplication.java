package com.payflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Kafka auto-configuration is excluded in Phase 1 so local startups only need Postgres + Redis.
 * Enable Kafka wiring in a later phase (outbox/webhooks) once brokers are required by domain code.
 */
@SpringBootApplication(exclude = {
		UserDetailsServiceAutoConfiguration.class,
		KafkaAutoConfiguration.class
})
public class PayflowBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PayflowBackendApplication.class, args);
	}

}

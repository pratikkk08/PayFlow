package com.payflow.webhooks.delivery;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(WebhookDeliveryProperties.class)
public class WebhookDeliveryConfig {

	private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryConfig.class);

	@Bean
	@ConditionalOnMissingBean(WebhookDnsResolver.class)
	WebhookDnsResolver webhookDnsResolver() {
		return InetAddress::getAllByName;
	}

	@Bean
	@ConditionalOnProperty(name = "payflow.webhooks.delivery.enabled", havingValue = "false", matchIfMissing = true)
	ApplicationRunner webhookDeliveryDisabledNotice() {
		return args -> log.info("Webhook HTTPS delivery disabled (payflow.webhooks.delivery.enabled=false). "
				+ "Delivery jobs may still be enqueued; outbound HTTP requires explicit enablement.");
	}

	@Configuration
	@ConditionalOnProperty(name = "payflow.webhooks.delivery.enabled", havingValue = "true")
	@ConditionalOnProperty(name = "payflow.webhooks.delivery.scheduling-enabled", havingValue = "true",
			matchIfMissing = true)
	@EnableScheduling
	static class SchedulingConfig {
	}
}

package com.payflow.webhooks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.payflow.webhooks.delivery.WebhookDnsResolver;
import com.payflow.webhooks.delivery.WebhookHttpTransport;

@TestConfiguration
public class WebhookDeliveryTestConfig {

	@Bean
	@Primary
	TestWebhookHttpTransport webhookHttpTransport() {
		return new TestWebhookHttpTransport();
	}

	@Bean
	@Primary
	TestDnsResolver webhookDnsResolver() {
		return new TestDnsResolver();
	}

	public static final class TestDnsResolver implements WebhookDnsResolver {
		private final AtomicReference<InetAddress[]> addresses = new AtomicReference<>();

		public TestDnsResolver() {
			try {
				setAddresses(InetAddress.getByName("8.8.8.8"));
			}
			catch (UnknownHostException ex) {
				throw new IllegalStateException(ex);
			}
		}

		public void setAddresses(InetAddress... resolved) {
			addresses.set(resolved);
		}

		@Override
		public InetAddress[] resolve(String hostname) {
			return addresses.get();
		}
	}
}

package com.payflow.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.payflow.webhooks.delivery.WebhookSignatureService;

class WebhookSignatureServiceTest {

	private final WebhookSignatureService signatures = new WebhookSignatureService();

	@Test
	void signatureIsDeterministicForFixedInputs() {
		String secret = "whsec_test_secret";
		String body = "{\"eventId\":\"11111111-1111-1111-1111-111111111111\"}";
		String eventId = "11111111-1111-1111-1111-111111111111";
		long ts = 1_700_000_000L;

		String first = signatures.sign(secret, ts, eventId, body);
		String second = signatures.sign(secret, ts, eventId, body);

		assertThat(first).isEqualTo(second).startsWith("v1=");
		assertThat(signatures.verify(secret, ts, eventId, body, first)).isTrue();
		assertThat(signatures.buildSignatureBase(ts, eventId, body))
				.isEqualTo(ts + "." + eventId + "." + body);
	}

	@Test
	void invalidSignatureFailsVerification() {
		String secret = "whsec_test_secret";
		String body = "{\"ok\":true}";
		String eventId = "22222222-2222-2222-2222-222222222222";
		long ts = 1_700_000_100L;
		String valid = signatures.sign(secret, ts, eventId, body);

		assertThat(signatures.verify(secret, ts, eventId, body, "v1=deadbeef")).isFalse();
		assertThat(signatures.verify(secret, ts, eventId, body + "x", valid)).isFalse();
		assertThat(signatures.verify("other-secret", ts, eventId, body, valid)).isFalse();
		assertThat(WebhookSignatureService.signaturesEqual(valid, valid)).isTrue();
		assertThat(WebhookSignatureService.signaturesEqual(valid, valid + "0")).isFalse();
	}
}

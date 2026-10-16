package com.payflow.webhooks.delivery;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 webhook request signing.
 *
 * <p><strong>Signature base (exact UTF-8 bytes):</strong>
 * {@code {unixTimestampSeconds}.{eventId}.{rawRequestBody}}
 *
 * <p><strong>Header value:</strong> {@code v1=} + lowercase hex HMAC-SHA256 of the signature base
 * using the endpoint signing secret as the key.
 *
 * <p><strong>Headers sent:</strong>
 * <ul>
 *   <li>{@code X-PayFlow-Event-ID} — stable event UUID (same across retries)</li>
 *   <li>{@code X-PayFlow-Timestamp} — unix seconds for this attempt</li>
 *   <li>{@code X-PayFlow-Signature} — {@code v1=<hex>}</li>
 * </ul>
 *
 * <p>Merchants should recompute the base from the received body + headers and compare
 * the hex digest with {@link #signaturesEqual(String, String)} (constant-time). Reject if
 * the timestamp is outside an acceptable skew window (application policy).
 *
 * <p>Never log the signing secret or the signature base string.
 */
@Component
public class WebhookSignatureService {

	public static final String HEADER_EVENT_ID = "X-PayFlow-Event-ID";
	public static final String HEADER_TIMESTAMP = "X-PayFlow-Timestamp";
	public static final String HEADER_SIGNATURE = "X-PayFlow-Signature";

	private static final String HMAC = "HmacSHA256";

	public String buildSignatureBase(long unixTimestampSeconds, String eventId, String body) {
		return unixTimestampSeconds + "." + eventId + "." + body;
	}

	public String sign(String signingSecret, long unixTimestampSeconds, String eventId, String body) {
		String base = buildSignatureBase(unixTimestampSeconds, eventId, body);
		return "v1=" + hmacHex(signingSecret, base);
	}

	public boolean verify(String signingSecret, long unixTimestampSeconds, String eventId, String body,
			String providedSignatureHeader) {
		if (providedSignatureHeader == null || providedSignatureHeader.isBlank()) {
			return false;
		}
		String expected = sign(signingSecret, unixTimestampSeconds, eventId, body);
		return signaturesEqual(expected, providedSignatureHeader.trim());
	}

	/** Constant-time compare of full {@code v1=<hex>} header values. */
	public static boolean signaturesEqual(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		byte[] left = a.getBytes(StandardCharsets.UTF_8);
		byte[] right = b.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(left, right);
	}

	private static String hmacHex(String secret, String base) {
		try {
			Mac mac = Mac.getInstance(HMAC);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
			byte[] digest = mac.doFinal(base.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("webhook_hmac_failed", ex);
		}
	}
}

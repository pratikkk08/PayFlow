package com.payflow.webhooks.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.payflow.shared.error.ApiException;
import com.payflow.shared.security.PayflowSecurityProperties;

/**
 * AES-256-GCM encryption for webhook signing secrets at rest.
 * Key material: {@code payflow.security.webhook.encryption-key-base64} (32 raw bytes).
 */
@Component
public class WebhookSecretCrypto {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_TAG_BITS = 128;
	private static final int IV_BYTES = 12;
	private static final int KEY_BYTES = 32;

	private final SecretKey key;
	private final SecureRandom secureRandom = new SecureRandom();

	public WebhookSecretCrypto(PayflowSecurityProperties properties) {
		this.key = loadKey(properties.getWebhook().getEncryptionKeyBase64());
	}

	public String encrypt(String plaintext) {
		try {
			byte[] iv = new byte[IV_BYTES];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
			buffer.put(iv);
			buffer.put(ciphertext);
			return Base64.getEncoder().encodeToString(buffer.array());
		}
		catch (GeneralSecurityException ex) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "webhook_secret_crypto_failed",
					"Failed to protect webhook signing secret");
		}
	}

	public String decrypt(String ciphertextBase64) {
		try {
			byte[] packed = Base64.getDecoder().decode(ciphertextBase64);
			if (packed.length <= IV_BYTES) {
				throw new IllegalArgumentException("ciphertext_too_short");
			}
			byte[] iv = Arrays.copyOfRange(packed, 0, IV_BYTES);
			byte[] ciphertext = Arrays.copyOfRange(packed, IV_BYTES, packed.length);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] plain = cipher.doFinal(ciphertext);
			return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException | ArrayIndexOutOfBoundsException ex) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "webhook_secret_crypto_failed",
					"Failed to read webhook signing secret");
		}
	}

	private static SecretKey loadKey(String base64Key) {
		if (base64Key == null || base64Key.isBlank()) {
			throw new IllegalStateException(
					"payflow.security.webhook.encryption-key-base64 / WEBHOOK_SECRET_ENCRYPTION_KEY is required");
		}
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64Key.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("WEBHOOK_SECRET_ENCRYPTION_KEY must be Base64-encoded", ex);
		}
		if (raw.length != KEY_BYTES) {
			throw new IllegalStateException(
					"WEBHOOK_SECRET_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256)");
		}
		return new SecretKeySpec(raw, "AES");
	}
}

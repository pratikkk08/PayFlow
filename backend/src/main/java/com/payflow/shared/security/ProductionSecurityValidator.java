package com.payflow.shared.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.util.StringUtils;

/**
 * Fail-fast checks for the {@code prod} profile. Never logs secret values.
 */
public final class ProductionSecurityValidator {

	private static final int MIN_PEPPER_LENGTH = 32;
	private static final int WEBHOOK_KEY_BYTES = 32;
	private static final Set<String> FORBIDDEN_PEPPER_FRAGMENTS = Set.of(
			"change-me",
			"change_me",
			"local-dev",
			"local_only",
			"not-for-production",
			"replace_with",
			"test-api-key-pepper");

	private ProductionSecurityValidator() {
	}

	public static void validate(PayflowSecurityProperties security, String datasourceUrl, String datasourceUsername,
			String datasourcePassword) {
		requireText(datasourceUrl, "DATABASE_URL / spring.datasource.url");
		requireText(datasourceUsername, "DATABASE_USERNAME / spring.datasource.username");
		requireText(datasourcePassword, "DATABASE_PASSWORD / spring.datasource.password");
		rejectLocalPlaceholder(datasourcePassword, "DATABASE_PASSWORD");

		PayflowSecurityProperties.Jwt jwt = security.getJwt();
		if (jwt.isAutoGenerateKeys()) {
			fail("payflow.security.jwt.auto-generate-keys must be false in production");
		}
		requireText(jwt.getPrivateKeyPath(), "JWT_PRIVATE_KEY_PATH");
		requireText(jwt.getPublicKeyPath(), "JWT_PUBLIC_KEY_PATH");
		requireReadableFile(jwt.getPrivateKeyPath(), "JWT_PRIVATE_KEY_PATH");
		requireReadableFile(jwt.getPublicKeyPath(), "JWT_PUBLIC_KEY_PATH");

		String pepper = security.getApiKey().getHashPepper();
		requireText(pepper, "API_KEY_HASH_PEPPER");
		if (pepper.length() < MIN_PEPPER_LENGTH) {
			fail("API_KEY_HASH_PEPPER must be at least " + MIN_PEPPER_LENGTH + " characters");
		}
		rejectLocalPlaceholder(pepper, "API_KEY_HASH_PEPPER");

		validateWebhookKey(security.getWebhook().getEncryptionKeyBase64());

		PayflowSecurityProperties.BrowserSession browser = security.getBrowserSession();
		if (!browser.isCookieSecure()) {
			fail("payflow.security.browser-session.cookie-secure must be true in production (HTTPS)");
		}
		String sameSite = browser.getCookieSameSite();
		if (!StringUtils.hasText(sameSite)
				|| !(sameSite.equalsIgnoreCase("Lax") || sameSite.equalsIgnoreCase("Strict"))) {
			fail("payflow.security.browser-session.cookie-same-site must be Lax or Strict in production");
		}

		List<String> origins = security.getCors().getAllowedOrigins();
		if (origins == null || origins.isEmpty()) {
			fail("PAYFLOW_CORS_ALLOWED_ORIGINS / payflow.security.cors.allowed-origins is required in production");
		}
		for (String origin : origins) {
			if (!StringUtils.hasText(origin)) {
				fail("CORS allowed-origins contains a blank entry");
			}
			String normalized = origin.trim().toLowerCase(Locale.ROOT);
			if (normalized.contains("localhost") || normalized.contains("127.0.0.1")) {
				fail("Production CORS must not include localhost / 127.0.0.1 origins");
			}
			if (!(normalized.startsWith("https://") || normalized.startsWith("http://"))) {
				fail("CORS origins must be absolute http(s) URLs");
			}
		}
	}

	static void validateWebhookKey(String base64Key) {
		requireText(base64Key, "WEBHOOK_SECRET_ENCRYPTION_KEY");
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64Key.trim());
		}
		catch (IllegalArgumentException ex) {
			fail("WEBHOOK_SECRET_ENCRYPTION_KEY must be valid Base64");
			return;
		}
		if (raw.length != WEBHOOK_KEY_BYTES) {
			fail("WEBHOOK_SECRET_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256)");
		}
	}

	private static void requireReadableFile(String pathValue, String name) {
		Path path = Path.of(pathValue).toAbsolutePath().normalize();
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			fail(name + " must point to a readable PEM file (missing: " + path + ")");
		}
	}

	private static void requireText(String value, String name) {
		if (!StringUtils.hasText(value)) {
			fail(name + " is required in production");
		}
	}

	private static void rejectLocalPlaceholder(String value, String name) {
		String lower = value.toLowerCase(Locale.ROOT);
		for (String fragment : FORBIDDEN_PEPPER_FRAGMENTS) {
			if (lower.contains(fragment)) {
				fail(name + " looks like a local/test placeholder and is not allowed in production");
			}
		}
	}

	private static void fail(String message) {
		throw new IllegalStateException("Production security check failed: " + message);
	}
}

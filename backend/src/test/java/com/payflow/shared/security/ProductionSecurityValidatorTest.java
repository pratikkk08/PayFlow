package com.payflow.shared.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductionSecurityValidatorTest {

	@TempDir
	Path tempDir;

	private Path privateKey;
	private Path publicKey;
	private PayflowSecurityProperties props;

	@BeforeEach
	void setUp() throws Exception {
		privateKey = tempDir.resolve("jwt-private.pem");
		publicKey = tempDir.resolve("jwt-public.pem");
		Files.writeString(privateKey, "-----BEGIN PRIVATE KEY-----\nMII=\n-----END PRIVATE KEY-----\n");
		Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\nMII=\n-----END PUBLIC KEY-----\n");

		props = new PayflowSecurityProperties();
		props.getJwt().setAutoGenerateKeys(false);
		props.getJwt().setPrivateKeyPath(privateKey.toString());
		props.getJwt().setPublicKeyPath(publicKey.toString());
		props.getApiKey().setHashPepper("production-grade-api-key-pepper-value-32b");
		props.getWebhook().setEncryptionKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
		props.getBrowserSession().setCookieSecure(true);
		props.getBrowserSession().setCookieSameSite("Lax");
		props.getCors().setAllowedOrigins(List.of("https://app.example.com"));
	}

	@Test
	void acceptsValidProductionConfiguration() {
		assertThatCode(() -> ProductionSecurityValidator.validate(
				props,
				"jdbc:postgresql://db.internal:5432/payflow",
				"payflow",
				"strong-database-password-not-local"))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsMissingJwtKeyFiles() {
		props.getJwt().setPrivateKeyPath(tempDir.resolve("missing-private.pem").toString());
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_PRIVATE_KEY_PATH");
	}

	@Test
	void rejectsAutoGenerateKeys() {
		props.getJwt().setAutoGenerateKeys(true);
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("auto-generate-keys");
	}

	@Test
	void rejectsMissingApiKeyPepper() {
		props.getApiKey().setHashPepper(" ");
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("API_KEY_HASH_PEPPER");
	}

	@Test
	void rejectsLocalPlaceholderPepper() {
		props.getApiKey().setHashPepper("change-me-local-pepper-still-too-weak!!!!");
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("placeholder");
	}

	@Test
	void rejectsShortPepper() {
		props.getApiKey().setHashPepper("short-but-not-a-placeholder");
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("at least 32");
	}

	@Test
	void rejectsMissingWebhookKey() {
		props.getWebhook().setEncryptionKeyBase64("");
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("WEBHOOK_SECRET_ENCRYPTION_KEY");
	}

	@Test
	void rejectsInvalidWebhookKeyLength() {
		props.getWebhook().setEncryptionKeyBase64(Base64.getEncoder().encodeToString("too-short".getBytes()));
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}

	@Test
	void rejectsInvalidWebhookKeyBase64() {
		props.getWebhook().setEncryptionKeyBase64("%%%not-base64%%%");
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Base64");
	}

	@Test
	void rejectsInsecureCookies() {
		props.getBrowserSession().setCookieSecure(false);
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cookie-secure");
	}

	@Test
	void rejectsLocalhostCorsOrigins() {
		props.getCors().setAllowedOrigins(List.of("http://localhost:5173"));
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("localhost");
	}

	@Test
	void rejectsEmptyCorsOrigins() {
		props.getCors().setAllowedOrigins(List.of());
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "strong-database-password-not-local"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CORS");
	}

	@Test
	void rejectsLocalDatabasePasswordPlaceholder() {
		assertThatThrownBy(() -> ProductionSecurityValidator.validate(
				props, "jdbc:postgresql://db:5432/payflow", "payflow", "change_me_local_only"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DATABASE_PASSWORD");
	}
}

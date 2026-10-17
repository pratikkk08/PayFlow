package com.payflow.shared.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.security")
public class PayflowSecurityProperties {

	private final Jwt jwt = new Jwt();
	private final RefreshToken refreshToken = new RefreshToken();
	private final BrowserSession browserSession = new BrowserSession();
	private final ApiKey apiKey = new ApiKey();
	private final RateLimit rateLimit = new RateLimit();
	private final Webhook webhook = new Webhook();

	public Jwt getJwt() {
		return jwt;
	}

	public RefreshToken getRefreshToken() {
		return refreshToken;
	}

	public BrowserSession getBrowserSession() {
		return browserSession;
	}

	public ApiKey getApiKey() {
		return apiKey;
	}

	public RateLimit getRateLimit() {
		return rateLimit;
	}

	public Webhook getWebhook() {
		return webhook;
	}

	public static class Jwt {
		private String issuer = "payflow";
		private String audience = "payflow-api";
		private Duration accessTokenTtl = Duration.ofMinutes(15);
		private String privateKeyPath = "local-secrets/jwt-private.pem";
		private String publicKeyPath = "local-secrets/jwt-public.pem";
		private boolean autoGenerateKeys = false;

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}

		public String getAudience() {
			return audience;
		}

		public void setAudience(String audience) {
			this.audience = audience;
		}

		public Duration getAccessTokenTtl() {
			return accessTokenTtl;
		}

		public void setAccessTokenTtl(Duration accessTokenTtl) {
			this.accessTokenTtl = accessTokenTtl;
		}

		public String getPrivateKeyPath() {
			return privateKeyPath;
		}

		public void setPrivateKeyPath(String privateKeyPath) {
			this.privateKeyPath = privateKeyPath;
		}

		public String getPublicKeyPath() {
			return publicKeyPath;
		}

		public void setPublicKeyPath(String publicKeyPath) {
			this.publicKeyPath = publicKeyPath;
		}

		public boolean isAutoGenerateKeys() {
			return autoGenerateKeys;
		}

		public void setAutoGenerateKeys(boolean autoGenerateKeys) {
			this.autoGenerateKeys = autoGenerateKeys;
		}
	}

	public static class RefreshToken {
		private Duration ttl = Duration.ofDays(7);

		public Duration getTtl() {
			return ttl;
		}

		public void setTtl(Duration ttl) {
			this.ttl = ttl;
		}
	}

	/**
	 * HttpOnly refresh cookie for SPA browser sessions.
	 * JSON {@code /api/v1/auth/login|refresh|logout} remain available for non-browser clients.
	 */
	public static class BrowserSession {
		private String refreshCookieName = "payflow_refresh";
		private String refreshCookiePath = "/api/v1/auth/browser";
		/** Must be false on plain HTTP localhost; true in production HTTPS. */
		private boolean cookieSecure = true;
		/** Lax | Strict | None */
		private String cookieSameSite = "Lax";

		public String getRefreshCookieName() {
			return refreshCookieName;
		}

		public void setRefreshCookieName(String refreshCookieName) {
			this.refreshCookieName = refreshCookieName;
		}

		public String getRefreshCookiePath() {
			return refreshCookiePath;
		}

		public void setRefreshCookiePath(String refreshCookiePath) {
			this.refreshCookiePath = refreshCookiePath;
		}

		public boolean isCookieSecure() {
			return cookieSecure;
		}

		public void setCookieSecure(boolean cookieSecure) {
			this.cookieSecure = cookieSecure;
		}

		public String getCookieSameSite() {
			return cookieSameSite;
		}

		public void setCookieSameSite(String cookieSameSite) {
			this.cookieSameSite = cookieSameSite;
		}
	}

	public static class ApiKey {
		private String hashPepper = "change-me-local-pepper";

		public String getHashPepper() {
			return hashPepper;
		}

		public void setHashPepper(String hashPepper) {
			this.hashPepper = hashPepper;
		}
	}

	public static class RateLimit {
		private boolean enabled = true;
		private int authRequestsPerMinute = 20;
		private boolean failClosedWhenRedisUnavailable = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getAuthRequestsPerMinute() {
			return authRequestsPerMinute;
		}

		public void setAuthRequestsPerMinute(int authRequestsPerMinute) {
			this.authRequestsPerMinute = authRequestsPerMinute;
		}

		public boolean isFailClosedWhenRedisUnavailable() {
			return failClosedWhenRedisUnavailable;
		}

		public void setFailClosedWhenRedisUnavailable(boolean failClosedWhenRedisUnavailable) {
			this.failClosedWhenRedisUnavailable = failClosedWhenRedisUnavailable;
		}
	}

	/**
	 * AES-256-GCM key for webhook signing secrets at rest (Base64 of 32 raw bytes).
	 * Env: {@code WEBHOOK_SECRET_ENCRYPTION_KEY}. Never commit real keys.
	 */
	public static class Webhook {
		private String encryptionKeyBase64 = "";

		public String getEncryptionKeyBase64() {
			return encryptionKeyBase64;
		}

		public void setEncryptionKeyBase64(String encryptionKeyBase64) {
			this.encryptionKeyBase64 = encryptionKeyBase64;
		}
	}
}

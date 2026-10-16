package com.payflow.webhooks.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpointEntity {

	@Id
	private UUID id;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Column(nullable = false, length = 2048)
	private String url;

	@Column(name = "signing_secret_ciphertext", nullable = false, columnDefinition = "text")
	private String signingSecretCiphertext;

	@Column(name = "secret_prefix", nullable = false, length = 24)
	private String secretPrefix;

	@Column(nullable = false)
	private boolean enabled;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "subscribed_event_types", nullable = false, columnDefinition = "jsonb")
	private List<String> subscribedEventTypes = new ArrayList<>();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "secret_rotated_at", nullable = false)
	private Instant secretRotatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected WebhookEndpointEntity() {
	}

	public WebhookEndpointEntity(UUID id, UUID merchantId, String url, String signingSecretCiphertext,
			String secretPrefix, boolean enabled, List<String> subscribedEventTypes, Instant now) {
		this.id = id;
		this.merchantId = merchantId;
		this.url = url;
		this.signingSecretCiphertext = signingSecretCiphertext;
		this.secretPrefix = secretPrefix;
		this.enabled = enabled;
		this.subscribedEventTypes = List.copyOf(subscribedEventTypes);
		this.createdAt = now;
		this.updatedAt = now;
		this.secretRotatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getSigningSecretCiphertext() {
		return signingSecretCiphertext;
	}

	public String getSecretPrefix() {
		return secretPrefix;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<String> getSubscribedEventTypes() {
		return List.copyOf(subscribedEventTypes);
	}

	public void setSubscribedEventTypes(List<String> subscribedEventTypes) {
		this.subscribedEventTypes = List.copyOf(subscribedEventTypes);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getSecretRotatedAt() {
		return secretRotatedAt;
	}

	public long getVersion() {
		return version;
	}

	public void touch(Instant now) {
		this.updatedAt = now;
	}
}

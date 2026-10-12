package com.payflow.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEventEntity {

	@Id
	private UUID id;

	@Column(name = "event_type", nullable = false, length = 64)
	private String eventType;

	@Column(name = "actor_user_id")
	private UUID actorUserId;

	@Column(name = "merchant_id")
	private UUID merchantId;

	@Column(name = "api_key_id")
	private UUID apiKeyId;

	@Column(length = 500)
	private String detail;

	@Column(name = "ip_address", length = 64)
	private String ipAddress;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected SecurityAuditEventEntity() {
	}

	public SecurityAuditEventEntity(UUID id, String eventType, UUID actorUserId, UUID merchantId, UUID apiKeyId,
			String detail, String ipAddress, Instant createdAt) {
		this.id = id;
		this.eventType = eventType;
		this.actorUserId = actorUserId;
		this.merchantId = merchantId;
		this.apiKeyId = apiKeyId;
		this.detail = detail;
		this.ipAddress = ipAddress;
		this.createdAt = createdAt;
	}
}

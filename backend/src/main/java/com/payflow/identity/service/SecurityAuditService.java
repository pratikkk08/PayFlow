package com.payflow.identity.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.identity.domain.SecurityAuditEventEntity;
import com.payflow.identity.persistence.SecurityAuditEventRepository;

@Service
public class SecurityAuditService {

	private final SecurityAuditEventRepository repository;

	public SecurityAuditService(SecurityAuditEventRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public void record(String eventType, UUID actorUserId, UUID merchantId, UUID apiKeyId, String detail,
			String ipAddress) {
		repository.save(new SecurityAuditEventEntity(
				UUID.randomUUID(),
				eventType,
				actorUserId,
				merchantId,
				apiKeyId,
				truncate(detail),
				ipAddress,
				Instant.now()));
	}

	private static String truncate(String detail) {
		if (detail == null) {
			return null;
		}
		return detail.length() <= 500 ? detail : detail.substring(0, 500);
	}
}

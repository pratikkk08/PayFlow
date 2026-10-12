package com.payflow.merchants.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.identity.service.SecurityAuditService;
import com.payflow.merchants.api.dto.ApiKeyCreatedResponse;
import com.payflow.merchants.api.dto.ApiKeyMetadataResponse;
import com.payflow.merchants.api.dto.CreateApiKeyRequest;
import com.payflow.merchants.domain.ApiKeyEntity;
import com.payflow.merchants.domain.ApiKeyMode;
import com.payflow.merchants.domain.ApiKeyType;
import com.payflow.merchants.persistence.ApiKeyRepository;
import com.payflow.shared.error.ApiException;
import com.payflow.shared.security.PayflowSecurityProperties;
import com.payflow.shared.security.SecurityHashes;
import com.payflow.shared.security.TokenGenerator;

@Service
public class ApiKeyService {

	private final ApiKeyRepository apiKeyRepository;
	private final MerchantService merchantService;
	private final PayflowSecurityProperties securityProperties;
	private final SecurityAuditService securityAuditService;

	public ApiKeyService(
			ApiKeyRepository apiKeyRepository,
			MerchantService merchantService,
			PayflowSecurityProperties securityProperties,
			SecurityAuditService securityAuditService) {
		this.apiKeyRepository = apiKeyRepository;
		this.merchantService = merchantService;
		this.securityProperties = securityProperties;
		this.securityAuditService = securityAuditService;
	}

	@Transactional
	public ApiKeyCreatedResponse create(UUID userId, UUID merchantId, CreateApiKeyRequest request) {
		merchantService.requireOwner(userId, merchantId);
		merchantService.requireAccessible(userId, merchantId);

		ApiKeyType type = request.type() == null ? ApiKeyType.SECRET : request.type();
		Instant now = Instant.now();
		UUID keyId = UUID.randomUUID();

		if (type == ApiKeyType.SECRET) {
			String raw = TokenGenerator.apiKey("sk_test_", 24);
			String prefix = raw.substring(0, Math.min(16, raw.length()));
			String hash = SecurityHashes.hmacSha256Hex(securityProperties.getApiKey().getHashPepper(), raw);
			ApiKeyEntity entity = ApiKeyEntity.secret(
					keyId, merchantId, request.name().trim(), ApiKeyMode.TEST, prefix, hash, userId, now);
			apiKeyRepository.save(entity);
			securityAuditService.record("API_KEY_CREATED", userId, merchantId, keyId, "secret key created", null);
			return new ApiKeyCreatedResponse(
					keyId, merchantId, entity.getName(), type, ApiKeyMode.TEST, prefix, raw, now);
		}

		String raw = TokenGenerator.apiKey("pk_test_", 24);
		String prefix = raw.substring(0, Math.min(16, raw.length()));
		ApiKeyEntity entity = ApiKeyEntity.publishable(
				keyId, merchantId, request.name().trim(), ApiKeyMode.TEST, prefix, raw, userId, now);
		apiKeyRepository.save(entity);
		securityAuditService.record("API_KEY_CREATED", userId, merchantId, keyId, "publishable key created", null);
		return new ApiKeyCreatedResponse(
				keyId, merchantId, entity.getName(), type, ApiKeyMode.TEST, prefix, raw, now);
	}

	@Transactional(readOnly = true)
	public List<ApiKeyMetadataResponse> list(UUID userId, UUID merchantId) {
		merchantService.requireAccessible(userId, merchantId);
		return apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
				.map(ApiKeyService::toMetadata)
				.toList();
	}

	@Transactional
	public void revoke(UUID userId, UUID merchantId, UUID keyId) {
		merchantService.requireOwner(userId, merchantId);
		ApiKeyEntity key = apiKeyRepository.findByIdAndMerchantId(keyId, merchantId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "API key not found"));
		if (!key.isRevoked()) {
			key.revoke(Instant.now());
			securityAuditService.record("API_KEY_REVOKED", userId, merchantId, keyId, "key revoked", null);
		}
	}

	private static ApiKeyMetadataResponse toMetadata(ApiKeyEntity key) {
		return new ApiKeyMetadataResponse(
				key.getId(),
				key.getMerchantId(),
				key.getName(),
				key.getKeyType(),
				key.getMode(),
				key.getKeyPrefix(),
				key.getCreatedAt(),
				key.getLastUsedAt(),
				key.getRevokedAt());
	}
}

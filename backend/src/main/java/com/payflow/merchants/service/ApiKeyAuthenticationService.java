package com.payflow.merchants.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.merchants.domain.ApiKeyEntity;
import com.payflow.merchants.domain.ApiKeyType;
import com.payflow.merchants.persistence.ApiKeyRepository;
import com.payflow.shared.security.ApiKeyAuthenticationToken;
import com.payflow.shared.security.PayflowSecurityProperties;
import com.payflow.shared.security.SecurityHashes;

@Service
public class ApiKeyAuthenticationService {

	private final ApiKeyRepository apiKeyRepository;
	private final PayflowSecurityProperties securityProperties;

	public ApiKeyAuthenticationService(ApiKeyRepository apiKeyRepository,
			PayflowSecurityProperties securityProperties) {
		this.apiKeyRepository = apiKeyRepository;
		this.securityProperties = securityProperties;
	}

	public boolean looksLikeSecretApiKey(String token) {
		return token != null && (token.startsWith("sk_test_") || token.startsWith("sk_live_"));
	}

	public boolean looksLikePublishableApiKey(String token) {
		return token != null && (token.startsWith("pk_test_") || token.startsWith("pk_live_"));
	}

	@Transactional
	public Optional<ApiKeyAuthenticationToken> authenticate(String rawSecretKey) {
		if (!looksLikeSecretApiKey(rawSecretKey)) {
			return Optional.empty();
		}
		String hash = SecurityHashes.hmacSha256Hex(securityProperties.getApiKey().getHashPepper(), rawSecretKey);
		Optional<ApiKeyEntity> found = apiKeyRepository.findBySecretHashAndRevokedAtIsNull(hash);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		ApiKeyEntity key = found.get();
		if (key.getKeyType() != ApiKeyType.SECRET) {
			return Optional.empty();
		}
		key.markUsed(Instant.now());
		return Optional.of(new ApiKeyAuthenticationToken(
				key.getMerchantId(), key.getId(), key.getKeyPrefix(), key.getKeyType().name()));
	}
}

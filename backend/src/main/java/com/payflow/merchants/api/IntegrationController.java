package com.payflow.merchants.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.merchants.api.dto.IntegrationMeResponse;
import com.payflow.merchants.domain.MerchantEntity;
import com.payflow.merchants.persistence.ApiKeyRepository;
import com.payflow.merchants.persistence.MerchantRepository;
import com.payflow.shared.error.ApiException;
import com.payflow.shared.security.ApiKeyAuthenticationToken;
import com.payflow.shared.security.SecuritySupport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations")
@SecurityRequirement(name = "bearer-api-key")
public class IntegrationController {

	private final SecuritySupport securitySupport;
	private final MerchantRepository merchantRepository;
	private final ApiKeyRepository apiKeyRepository;

	public IntegrationController(SecuritySupport securitySupport, MerchantRepository merchantRepository,
			ApiKeyRepository apiKeyRepository) {
		this.securitySupport = securitySupport;
		this.merchantRepository = merchantRepository;
		this.apiKeyRepository = apiKeyRepository;
	}

	@GetMapping("/me")
	@Operation(summary = "Identify the merchant authenticated by a secret API key")
	public IntegrationMeResponse me() {
		ApiKeyAuthenticationToken auth = securitySupport.requireApiKeyAuth();
		MerchantEntity merchant = merchantRepository.findById(auth.getMerchantId())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid API key"));
		var key = apiKeyRepository.findById(auth.getApiKeyId())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid API key"));
		return new IntegrationMeResponse(
				merchant.getId(),
				merchant.getName(),
				merchant.getStatus(),
				key.getId(),
				key.getKeyPrefix(),
				key.getKeyType(),
				key.getMode());
	}
}

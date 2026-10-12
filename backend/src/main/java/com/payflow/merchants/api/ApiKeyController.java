package com.payflow.merchants.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.merchants.api.dto.ApiKeyCreatedResponse;
import com.payflow.merchants.api.dto.ApiKeyMetadataResponse;
import com.payflow.merchants.api.dto.CreateApiKeyRequest;
import com.payflow.merchants.service.ApiKeyService;
import com.payflow.shared.security.SecuritySupport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/api-keys")
@Tag(name = "API Keys")
@SecurityRequirement(name = "bearer-jwt")
public class ApiKeyController {

	private final ApiKeyService apiKeyService;
	private final SecuritySupport securitySupport;

	public ApiKeyController(ApiKeyService apiKeyService, SecuritySupport securitySupport) {
		this.apiKeyService = apiKeyService;
		this.securitySupport = securitySupport;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create an API key (secret returned once)")
	public ApiKeyCreatedResponse create(@PathVariable UUID merchantId,
			@Valid @RequestBody CreateApiKeyRequest request) {
		return apiKeyService.create(securitySupport.requireUserId(), merchantId, request);
	}

	@GetMapping
	@Operation(summary = "List API key metadata for a merchant")
	public List<ApiKeyMetadataResponse> list(@PathVariable UUID merchantId) {
		return apiKeyService.list(securitySupport.requireUserId(), merchantId);
	}

	@DeleteMapping("/{keyId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoke an API key (idempotent)")
	public void revoke(@PathVariable UUID merchantId, @PathVariable UUID keyId) {
		apiKeyService.revoke(securitySupport.requireUserId(), merchantId, keyId);
	}
}

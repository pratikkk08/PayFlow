package com.payflow.webhooks.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.merchants.service.MerchantService;
import com.payflow.outbox.domain.OutboxEventType;
import com.payflow.shared.error.ApiException;
import com.payflow.shared.security.TokenGenerator;
import com.payflow.webhooks.api.dto.CreateWebhookEndpointRequest;
import com.payflow.webhooks.api.dto.UpdateWebhookEndpointRequest;
import com.payflow.webhooks.api.dto.WebhookEndpointCreatedResponse;
import com.payflow.webhooks.api.dto.WebhookEndpointResponse;
import com.payflow.webhooks.domain.WebhookEndpointEntity;
import com.payflow.webhooks.persistence.WebhookEndpointRepository;

@Service
public class WebhookEndpointService {

	private static final String SECRET_PREFIX_TOKEN = "whsec_";

	private final WebhookEndpointRepository webhookEndpointRepository;
	private final MerchantService merchantService;
	private final WebhookUrlValidator urlValidator;
	private final WebhookSecretCrypto secretCrypto;

	public WebhookEndpointService(WebhookEndpointRepository webhookEndpointRepository,
			MerchantService merchantService,
			WebhookUrlValidator urlValidator,
			WebhookSecretCrypto secretCrypto) {
		this.webhookEndpointRepository = webhookEndpointRepository;
		this.merchantService = merchantService;
		this.urlValidator = urlValidator;
		this.secretCrypto = secretCrypto;
	}

	@Transactional
	public WebhookEndpointCreatedResponse create(UUID userId, UUID merchantId, CreateWebhookEndpointRequest request) {
		merchantService.requireOwner(userId, merchantId);

		String url = urlValidator.validateForRegistration(request.url());
		List<String> eventTypes = normalizeEventTypes(request.eventTypes());
		boolean enabled = request.enabled() == null || request.enabled();

		if (webhookEndpointRepository.existsByMerchantIdAndUrl(merchantId, url)) {
			throw new ApiException(HttpStatus.CONFLICT, "conflict", "Webhook URL already registered for this merchant");
		}

		String rawSecret = TokenGenerator.apiKey(SECRET_PREFIX_TOKEN, 32);
		String ciphertext = secretCrypto.encrypt(rawSecret);
		String secretPrefix = rawSecret.substring(0, Math.min(16, rawSecret.length()));
		Instant now = Instant.now();
		UUID id = UUID.randomUUID();

		WebhookEndpointEntity entity = new WebhookEndpointEntity(
				id, merchantId, url, ciphertext, secretPrefix, enabled, eventTypes, now);
		webhookEndpointRepository.saveAndFlush(entity);

		return new WebhookEndpointCreatedResponse(
				entity.getId(),
				entity.getMerchantId(),
				entity.getUrl(),
				entity.getSecretPrefix(),
				rawSecret,
				entity.isEnabled(),
				entity.getSubscribedEventTypes(),
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				entity.getSecretRotatedAt(),
				entity.getVersion());
	}

	@Transactional(readOnly = true)
	public List<WebhookEndpointResponse> list(UUID userId, UUID merchantId) {
		merchantService.requireOwner(userId, merchantId);
		return webhookEndpointRepository.findByMerchantIdOrderByCreatedAtDescIdDesc(merchantId).stream()
				.map(WebhookEndpointService::toResponse)
				.toList();
	}

	@Transactional
	public WebhookEndpointResponse update(UUID userId, UUID merchantId, UUID webhookId,
			UpdateWebhookEndpointRequest request) {
		merchantService.requireOwner(userId, merchantId);
		WebhookEndpointEntity entity = webhookEndpointRepository.findByIdAndMerchantId(webhookId, merchantId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Webhook endpoint not found"));

		if (request.url() == null && request.eventTypes() == null && request.enabled() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "No webhook fields to update");
		}

		if (request.url() != null) {
			String url = urlValidator.validateForRegistration(request.url());
			if (!url.equals(entity.getUrl()) && webhookEndpointRepository.existsByMerchantIdAndUrl(merchantId, url)) {
				throw new ApiException(HttpStatus.CONFLICT, "conflict",
						"Webhook URL already registered for this merchant");
			}
			entity.setUrl(url);
		}
		if (request.eventTypes() != null) {
			entity.setSubscribedEventTypes(normalizeEventTypes(request.eventTypes()));
		}
		if (request.enabled() != null) {
			entity.setEnabled(request.enabled());
		}
		entity.touch(Instant.now());
		webhookEndpointRepository.saveAndFlush(entity);
		return toResponse(entity);
	}

	@Transactional
	public void delete(UUID userId, UUID merchantId, UUID webhookId) {
		merchantService.requireOwner(userId, merchantId);
		WebhookEndpointEntity entity = webhookEndpointRepository.findByIdAndMerchantId(webhookId, merchantId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Webhook endpoint not found"));
		webhookEndpointRepository.delete(entity);
	}

	private static List<String> normalizeEventTypes(List<String> raw) {
		if (raw == null || raw.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_event_types",
					"At least one subscribed event type is required");
		}
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String value : raw) {
			if (value == null || value.isBlank()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_event_types",
						"Event types must be non-blank");
			}
			String wire = value.trim();
			if (OutboxEventType.fromWireValue(wire).isEmpty()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_event_types",
						"Unsupported event type: " + wire);
			}
			unique.add(wire);
		}
		return new ArrayList<>(unique);
	}

	private static WebhookEndpointResponse toResponse(WebhookEndpointEntity entity) {
		return new WebhookEndpointResponse(
				entity.getId(),
				entity.getMerchantId(),
				entity.getUrl(),
				entity.getSecretPrefix(),
				entity.isEnabled(),
				entity.getSubscribedEventTypes(),
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				entity.getSecretRotatedAt(),
				entity.getVersion());
	}
}

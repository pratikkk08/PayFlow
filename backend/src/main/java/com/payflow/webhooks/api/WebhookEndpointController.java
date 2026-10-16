package com.payflow.webhooks.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.shared.security.SecuritySupport;
import com.payflow.webhooks.api.dto.CreateWebhookEndpointRequest;
import com.payflow.webhooks.api.dto.UpdateWebhookEndpointRequest;
import com.payflow.webhooks.api.dto.WebhookEndpointCreatedResponse;
import com.payflow.webhooks.api.dto.WebhookEndpointResponse;
import com.payflow.webhooks.service.WebhookEndpointService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/webhooks")
@Tag(name = "Webhooks")
@SecurityRequirement(name = "bearer-jwt")
public class WebhookEndpointController {

	private final WebhookEndpointService webhookEndpointService;
	private final SecuritySupport securitySupport;

	public WebhookEndpointController(WebhookEndpointService webhookEndpointService,
			SecuritySupport securitySupport) {
		this.webhookEndpointService = webhookEndpointService;
		this.securitySupport = securitySupport;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Register a webhook endpoint (signing secret returned once)")
	public WebhookEndpointCreatedResponse create(@PathVariable UUID merchantId,
			@Valid @RequestBody CreateWebhookEndpointRequest request) {
		return webhookEndpointService.create(securitySupport.requireUserId(), merchantId, request);
	}

	@GetMapping
	@Operation(summary = "List webhook endpoints for a merchant (secrets never included)")
	public List<WebhookEndpointResponse> list(@PathVariable UUID merchantId) {
		return webhookEndpointService.list(securitySupport.requireUserId(), merchantId);
	}

	@PatchMapping("/{webhookId}")
	@Operation(summary = "Update a webhook endpoint URL, subscriptions, or enabled flag")
	public WebhookEndpointResponse update(@PathVariable UUID merchantId, @PathVariable UUID webhookId,
			@Valid @RequestBody UpdateWebhookEndpointRequest request) {
		return webhookEndpointService.update(securitySupport.requireUserId(), merchantId, webhookId, request);
	}

	@DeleteMapping("/{webhookId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a webhook endpoint")
	public void delete(@PathVariable UUID merchantId, @PathVariable UUID webhookId) {
		webhookEndpointService.delete(securitySupport.requireUserId(), merchantId, webhookId);
	}
}

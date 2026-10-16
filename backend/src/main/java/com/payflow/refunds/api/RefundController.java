package com.payflow.refunds.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.refunds.api.dto.CreateRefundRequest;
import com.payflow.refunds.api.dto.RefundPageResponse;
import com.payflow.refunds.api.dto.RefundResponse;
import com.payflow.refunds.service.RefundService;
import com.payflow.shared.security.SecuritySupport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Refunds")
@SecurityRequirement(name = "bearer-jwt")
public class RefundController {

	private final RefundService refundService;
	private final SecuritySupport securitySupport;

	public RefundController(RefundService refundService, SecuritySupport securitySupport) {
		this.refundService = refundService;
		this.securitySupport = securitySupport;
	}

	@PostMapping("/api/v1/payments/{paymentId}/refunds")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a simulated full or partial refund (Idempotency-Key required)")
	public RefundResponse create(
			@PathVariable UUID paymentId,
			@Parameter(description = "Client idempotency key scoped to the merchant + REFUND_CREATE", required = true)
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CreateRefundRequest request) {
		return refundService.create(securitySupport.requireUserId(), paymentId, idempotencyKey, request);
	}

	@GetMapping("/api/v1/refunds/{refundId}")
	@Operation(summary = "Get a refund by id (owner only)")
	public RefundResponse get(@PathVariable UUID refundId) {
		return refundService.getForOwner(securitySupport.requireUserId(), refundId);
	}

	@GetMapping("/api/v1/payments/{paymentId}/refunds")
	@Operation(summary = "List refunds for a payment (owner only), newest first")
	public RefundPageResponse list(
			@PathVariable UUID paymentId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return refundService.listForPaymentOwner(securitySupport.requireUserId(), paymentId, page, size);
	}
}

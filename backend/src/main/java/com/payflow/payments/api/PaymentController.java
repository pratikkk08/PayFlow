package com.payflow.payments.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.payments.api.dto.CreatePaymentRequest;
import com.payflow.payments.api.dto.PaymentPageResponse;
import com.payflow.payments.api.dto.PaymentResponse;
import com.payflow.payments.domain.PaymentStatus;
import com.payflow.payments.service.PaymentService;
import com.payflow.shared.security.SecuritySupport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
@SecurityRequirement(name = "bearer-jwt")
public class PaymentController {

	private final PaymentService paymentService;
	private final SecuritySupport securitySupport;

	public PaymentController(PaymentService paymentService, SecuritySupport securitySupport) {
		this.paymentService = paymentService;
		this.securitySupport = securitySupport;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a payment in CREATED status (Idempotency-Key required)")
	public PaymentResponse create(
			@Parameter(description = "Client idempotency key scoped to the merchant", required = true)
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CreatePaymentRequest request) {
		return paymentService.create(securitySupport.requireUserId(), idempotencyKey, request);
	}

	@GetMapping("/{paymentId}")
	@Operation(summary = "Get a payment by id (owner only)")
	public PaymentResponse get(@PathVariable UUID paymentId) {
		return paymentService.getForOwner(securitySupport.requireUserId(), paymentId);
	}

	@GetMapping
	@Operation(summary = "List payments for a merchant with optional filters and pagination")
	public PaymentPageResponse list(
			@RequestParam UUID merchantId,
			@RequestParam(required = false) PaymentStatus status,
			@RequestParam(required = false) Instant createdFrom,
			@RequestParam(required = false) Instant createdTo,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return paymentService.listForOwner(securitySupport.requireUserId(), merchantId, status, createdFrom, createdTo,
				page, size);
	}

	@PostMapping("/{paymentId}/authorize")
	@Operation(summary = "Run risk evaluation then simulated authorization (CREATED → AUTHORIZED/FAILED, or held CREATED on REVIEW)")
	public PaymentResponse authorize(@PathVariable UUID paymentId) {
		return paymentService.authorize(securitySupport.requireUserId(), paymentId);
	}

	@PostMapping("/{paymentId}/capture")
	@Operation(summary = "Simulate capture (AUTHORIZED → CAPTURED; not real settlement)")
	public PaymentResponse capture(@PathVariable UUID paymentId) {
		return paymentService.capture(securitySupport.requireUserId(), paymentId);
	}

	@PostMapping("/{paymentId}/cancel")
	@Operation(summary = "Cancel a payment (CREATED or AUTHORIZED → CANCELLED)")
	public PaymentResponse cancel(@PathVariable UUID paymentId) {
		return paymentService.cancel(securitySupport.requireUserId(), paymentId);
	}
}

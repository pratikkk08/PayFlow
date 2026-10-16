package com.payflow.payments.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.ledger.service.LedgerPostingService;
import com.payflow.merchants.persistence.MerchantRepository;
import com.payflow.merchants.service.MerchantService;
import com.payflow.outbox.domain.OutboxEventType;
import com.payflow.outbox.service.OutboxEventWriter;
import com.payflow.payments.api.dto.CreatePaymentRequest;
import com.payflow.payments.api.dto.PaymentPageResponse;
import com.payflow.payments.api.dto.PaymentResponse;
import com.payflow.payments.domain.IdempotencyRecordStatus;
import com.payflow.payments.domain.PaymentEntity;
import com.payflow.payments.domain.PaymentIdempotencyRecordEntity;
import com.payflow.payments.domain.PaymentStatus;
import com.payflow.payments.persistence.PaymentIdempotencyRepository;
import com.payflow.payments.persistence.PaymentRepository;
import com.payflow.payments.persistence.PaymentSpecifications;
import com.payflow.payments.processor.AuthorizationResult;
import com.payflow.payments.processor.SimulatedPaymentProcessor;
import com.payflow.risk.domain.RiskAssessmentEntity;
import com.payflow.risk.domain.RiskDecision;
import com.payflow.risk.persistence.RiskAssessmentRepository;
import com.payflow.risk.service.RiskAssessmentWriter;
import com.payflow.risk.service.RiskEvaluationResult;
import com.payflow.risk.service.RiskEvaluationService;
import com.payflow.shared.error.ApiException;

import tools.jackson.databind.json.JsonMapper;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(72);
	static final Duration STARTED_LEASE = Duration.ofSeconds(30);

	/** Safe failure code when risk decision is BLOCK (no processor call). */
	public static final String FAILURE_RISK_BLOCKED = "risk_blocked";

	private final PaymentRepository paymentRepository;
	private final PaymentIdempotencyRepository idempotencyRepository;
	private final MerchantService merchantService;
	private final MerchantRepository merchantRepository;
	private final IdempotencyKeyValidator idempotencyKeyValidator;
	private final SimulatedPaymentProcessor paymentProcessor;
	private final LedgerPostingService ledgerPostingService;
	private final OutboxEventWriter outboxEventWriter;
	private final RiskEvaluationService riskEvaluationService;
	private final RiskAssessmentWriter riskAssessmentWriter;
	private final RiskAssessmentRepository riskAssessmentRepository;
	private final Clock clock;
	private final JsonMapper jsonMapper;

	public PaymentService(PaymentRepository paymentRepository,
			PaymentIdempotencyRepository idempotencyRepository,
			MerchantService merchantService,
			MerchantRepository merchantRepository,
			IdempotencyKeyValidator idempotencyKeyValidator,
			SimulatedPaymentProcessor paymentProcessor,
			LedgerPostingService ledgerPostingService,
			OutboxEventWriter outboxEventWriter,
			RiskEvaluationService riskEvaluationService,
			RiskAssessmentWriter riskAssessmentWriter,
			RiskAssessmentRepository riskAssessmentRepository,
			Clock clock,
			JsonMapper jsonMapper) {
		this.paymentRepository = paymentRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.merchantService = merchantService;
		this.merchantRepository = merchantRepository;
		this.idempotencyKeyValidator = idempotencyKeyValidator;
		this.paymentProcessor = paymentProcessor;
		this.ledgerPostingService = ledgerPostingService;
		this.outboxEventWriter = outboxEventWriter;
		this.riskEvaluationService = riskEvaluationService;
		this.riskAssessmentWriter = riskAssessmentWriter;
		this.riskAssessmentRepository = riskAssessmentRepository;
		this.clock = clock;
		this.jsonMapper = jsonMapper;
	}

	@Transactional
	public PaymentResponse create(UUID userId, String rawIdempotencyKey, CreatePaymentRequest request) {
		String idempotencyKey = idempotencyKeyValidator.requireValid(rawIdempotencyKey);
		merchantService.requireOwner(userId, request.merchantId());

		String fingerprint = PaymentRequestFingerprint.of(request);
		Instant now = Instant.now();
		Instant expiresAt = now.plus(IDEMPOTENCY_RETENTION);

		boolean reserved = idempotencyRepository.tryReserve(
				UUID.randomUUID(),
				request.merchantId(),
				PaymentIdempotencyRecordEntity.OPERATION_CREATE_PAYMENT,
				idempotencyKey,
				fingerprint,
				now,
				expiresAt);

		if (!reserved) {
			PaymentIdempotencyRecordEntity existing = idempotencyRepository
					.findForUpdate(
							request.merchantId(),
							PaymentIdempotencyRecordEntity.OPERATION_CREATE_PAYMENT,
							idempotencyKey)
					.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "idempotency_in_progress",
							"Idempotent request is still processing; retry shortly"));
			return handleExisting(existing, fingerprint, now, expiresAt, request);
		}

		PaymentIdempotencyRecordEntity record = idempotencyRepository
				.findForUpdate(
						request.merchantId(),
						PaymentIdempotencyRecordEntity.OPERATION_CREATE_PAYMENT,
						idempotencyKey)
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
						"Idempotency reservation missing after insert"));

		return createPaymentAndComplete(record, request, now);
	}

	@Transactional
	public PaymentResponse authorize(UUID userId, UUID paymentId) {
		PaymentEntity payment = requireOwnedPaymentForUpdate(userId, paymentId);

		if (payment.getStatus() != PaymentStatus.CREATED) {
			throw invalidTransition("Payment status " + payment.getStatus() + " cannot be authorized");
		}

		var existingAssessment = riskAssessmentRepository.findByPaymentId(paymentId);
		if (existingAssessment.isPresent()) {
			RiskAssessmentEntity prior = existingAssessment.get();
			if (prior.getDecision() == RiskDecision.REVIEW) {
				throw invalidTransition("Payment is held for risk review and cannot be authorized");
			}
			throw invalidTransition("Payment already has a risk assessment and cannot be re-authorized");
		}

		// Lock order: payment (above) → merchant. Serializes risk velocity/failures per merchant.
		merchantRepository.findByIdForUpdate(payment.getMerchantId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Merchant not found"));

		RiskEvaluationResult risk = riskEvaluationService.evaluateForAuthorization(payment);
		riskAssessmentWriter.persist(risk);
		logRiskDecision(risk);

		// Shared Clock with risk evaluation so FAILED.updated_at aligns with attempt windows.
		Instant now = clock.instant();
		if (risk.decision() == RiskDecision.REVIEW) {
			// Held: assessment committed with CREATED; no processor, outbox auth/fail, or ledger.
			return toResponse(payment);
		}
		if (risk.decision() == RiskDecision.BLOCK) {
			try {
				payment.applyAuthorizationDeclined(FAILURE_RISK_BLOCKED, now);
			}
			catch (IllegalStateException ex) {
				throw invalidTransition(ex.getMessage());
			}
			paymentRepository.saveAndFlush(payment);
			outboxEventWriter.appendPaymentEvent(payment, OutboxEventType.PAYMENT_FAILED);
			return toResponse(payment);
		}

		// ALLOW and FLAG: proceed with simulated processor (assessment already persisted).
		AuthorizationResult result = paymentProcessor.authorize(payment);
		try {
			if (result.approved()) {
				payment.applyAuthorizationApproved(result.processorRef(), now);
			}
			else {
				payment.applyAuthorizationDeclined(result.failureCode(), now);
			}
		}
		catch (IllegalStateException ex) {
			throw invalidTransition(ex.getMessage());
		}
		paymentRepository.saveAndFlush(payment);
		outboxEventWriter.appendPaymentEvent(payment,
				result.approved() ? OutboxEventType.PAYMENT_AUTHORIZED : OutboxEventType.PAYMENT_FAILED);
		return toResponse(payment);
	}

	/** Safe identifiers/reason codes only — never secrets or rule parameter payloads. */
	private static void logRiskDecision(RiskEvaluationResult risk) {
		log.info(
				"risk_authorize paymentId={} merchantId={} decision={} score={} reasons={} matchedRules={}",
				risk.paymentId(),
				risk.merchantId(),
				risk.decision(),
				risk.riskScore(),
				risk.reasonCodes(),
				risk.matchedRuleIds().size());
	}

	@Transactional
	public PaymentResponse capture(UUID userId, UUID paymentId) {
		PaymentEntity payment = requireOwnedPaymentForUpdate(userId, paymentId);
		if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
			throw invalidTransition("Payment status " + payment.getStatus() + " cannot be captured");
		}
		try {
			payment.applyCapture(Instant.now());
		}
		catch (IllegalStateException ex) {
			throw invalidTransition(ex.getMessage());
		}
		paymentRepository.saveAndFlush(payment);
		ledgerPostingService.postPaymentCapture(payment);
		outboxEventWriter.appendPaymentEvent(payment, OutboxEventType.PAYMENT_CAPTURED);
		return toResponse(payment);
	}

	@Transactional
	public PaymentResponse cancel(UUID userId, UUID paymentId) {
		PaymentEntity payment = requireOwnedPaymentForUpdate(userId, paymentId);
		if (payment.getStatus() != PaymentStatus.CREATED && payment.getStatus() != PaymentStatus.AUTHORIZED) {
			throw invalidTransition("Payment status " + payment.getStatus() + " cannot be cancelled");
		}
		try {
			payment.applyCancel(Instant.now());
		}
		catch (IllegalStateException ex) {
			throw invalidTransition(ex.getMessage());
		}
		paymentRepository.saveAndFlush(payment);
		outboxEventWriter.appendPaymentEvent(payment, OutboxEventType.PAYMENT_CANCELLED);
		return toResponse(payment);
	}

	private static final int MAX_PAGE_SIZE = 100;

	@Transactional(readOnly = true)
	public PaymentResponse getForOwner(UUID userId, UUID paymentId) {
		return toResponse(requireOwnedPayment(userId, paymentId));
	}

	@Transactional(readOnly = true)
	public PaymentPageResponse listForOwner(UUID userId, UUID merchantId, PaymentStatus status, Instant createdFrom,
			Instant createdTo, int page, int size) {
		merchantService.requireOwner(userId, merchantId);
		validateListParams(createdFrom, createdTo, page, size);

		PageRequest pageable = PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		Page<PaymentEntity> result = paymentRepository.findAll(
				PaymentSpecifications.filtered(merchantId, status, createdFrom, createdTo),
				pageable);
		return new PaymentPageResponse(
				result.getContent().stream().map(PaymentService::toResponse).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	private static void validateListParams(Instant createdFrom, Instant createdTo, int page, int size) {
		if (page < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "page must be >= 0");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error",
					"size must be between 1 and " + MAX_PAGE_SIZE);
		}
		if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error",
					"createdFrom must be less than or equal to createdTo");
		}
	}

	private static ApiException invalidTransition(String message) {
		return new ApiException(HttpStatus.CONFLICT, "invalid_state_transition", message);
	}

	private PaymentEntity requireOwnedPayment(UUID userId, UUID paymentId) {
		PaymentEntity payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Payment not found"));
		merchantService.requireOwner(userId, payment.getMerchantId());
		return payment;
	}

	private PaymentEntity requireOwnedPaymentForUpdate(UUID userId, UUID paymentId) {
		PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Payment not found"));
		merchantService.requireOwner(userId, payment.getMerchantId());
		return payment;
	}

	private PaymentResponse handleExisting(PaymentIdempotencyRecordEntity existing, String fingerprint, Instant now,
			Instant expiresAt, CreatePaymentRequest request) {
		if (existing.getStatus() == IdempotencyRecordStatus.COMPLETED) {
			if (!existing.getRequestFingerprint().equals(fingerprint)) {
				throw new ApiException(HttpStatus.CONFLICT, "idempotency_key_reuse",
						"Idempotency-Key was already used with a different request");
			}
			return readStoredResponse(existing);
		}

		if (existing.getStatus() == IdempotencyRecordStatus.STARTED) {
			boolean leaseExpired = existing.getLockedAt().isBefore(now.minus(STARTED_LEASE));
			if (!leaseExpired) {
				throw new ApiException(HttpStatus.CONFLICT, "idempotency_in_progress",
						"Idempotent request is still processing; retry shortly");
			}
			if (!existing.getRequestFingerprint().equals(fingerprint)) {
				throw new ApiException(HttpStatus.CONFLICT, "idempotency_key_reuse",
						"Idempotency-Key was already used with a different request");
			}
			existing.reclaimStarted(fingerprint, now, expiresAt);
			idempotencyRepository.saveAndFlush(existing);
			return createPaymentAndComplete(existing, request, now);
		}

		throw new ApiException(HttpStatus.CONFLICT, "idempotency_in_progress",
				"Idempotent request is still processing; retry shortly");
	}

	private PaymentResponse createPaymentAndComplete(PaymentIdempotencyRecordEntity record,
			CreatePaymentRequest request, Instant now) {
		PaymentEntity payment = new PaymentEntity(
				UUID.randomUUID(),
				request.merchantId(),
				request.merchantReference().trim(),
				request.amountMinor(),
				request.currency(),
				PaymentStatus.CREATED,
				normalizeDescription(request.description()),
				now);

		try {
			paymentRepository.saveAndFlush(payment);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "conflict",
					"A payment with this merchant reference already exists");
		}

		outboxEventWriter.appendPaymentEvent(payment, OutboxEventType.PAYMENT_CREATED);

		PaymentResponse response = toResponse(payment);
		String responseJson;
		try {
			responseJson = jsonMapper.writeValueAsString(response);
		}
		catch (RuntimeException ex) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
					"Failed to persist idempotent response");
		}

		record.markCompleted(payment.getId(), HttpStatus.CREATED.value(), responseJson, Instant.now());
		idempotencyRepository.saveAndFlush(record);
		return response;
	}

	private PaymentResponse readStoredResponse(PaymentIdempotencyRecordEntity existing) {
		try {
			return jsonMapper.readValue(existing.getResponseJson(), PaymentResponse.class);
		}
		catch (RuntimeException ex) {
			return paymentRepository.findById(existing.getPaymentId())
					.map(PaymentService::toResponse)
					.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
							"Stored idempotent payment is unavailable"));
		}
	}

	private static String normalizeDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.trim();
	}

	private static PaymentResponse toResponse(PaymentEntity payment) {
		return new PaymentResponse(
				payment.getId(),
				payment.getMerchantId(),
				payment.getMerchantReference(),
				payment.getAmountMinor(),
				payment.getCurrency(),
				payment.getStatus(),
				payment.getFailureCode(),
				payment.getAuthorizedAt(),
				payment.getCapturedAt(),
				payment.getCancelledAt(),
				payment.getCreatedAt());
	}
}

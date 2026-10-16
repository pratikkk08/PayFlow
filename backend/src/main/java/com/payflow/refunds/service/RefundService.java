package com.payflow.refunds.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.ledger.service.LedgerPostingService;
import com.payflow.merchants.service.MerchantService;
import com.payflow.outbox.service.OutboxEventWriter;
import com.payflow.payments.domain.IdempotencyRecordStatus;
import com.payflow.payments.domain.PaymentEntity;
import com.payflow.payments.domain.PaymentStatus;
import com.payflow.payments.persistence.PaymentRepository;
import com.payflow.payments.service.IdempotencyKeyValidator;
import com.payflow.refunds.api.dto.CreateRefundRequest;
import com.payflow.refunds.api.dto.RefundPageResponse;
import com.payflow.refunds.api.dto.RefundResponse;
import com.payflow.refunds.domain.RefundEntity;
import com.payflow.refunds.domain.RefundIdempotencyRecordEntity;
import com.payflow.refunds.domain.RefundStatus;
import com.payflow.refunds.persistence.RefundIdempotencyRepository;
import com.payflow.refunds.persistence.RefundRepository;
import com.payflow.refunds.processor.RefundResult;
import com.payflow.refunds.processor.SimulatedRefundProcessor;
import com.payflow.shared.error.ApiException;

import tools.jackson.databind.json.JsonMapper;

@Service
public class RefundService {

	static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(72);
	static final Duration STARTED_LEASE = Duration.ofSeconds(30);
	private static final int MAX_PAGE_SIZE = 100;

	private static final EnumSet<RefundStatus> CAPACITY_RESERVING_STATUSES = EnumSet.of(
			RefundStatus.PENDING, RefundStatus.SUCCEEDED);

	private final PaymentRepository paymentRepository;
	private final RefundRepository refundRepository;
	private final RefundIdempotencyRepository idempotencyRepository;
	private final MerchantService merchantService;
	private final IdempotencyKeyValidator idempotencyKeyValidator;
	private final SimulatedRefundProcessor refundProcessor;
	private final LedgerPostingService ledgerPostingService;
	private final OutboxEventWriter outboxEventWriter;
	private final JsonMapper jsonMapper;

	public RefundService(PaymentRepository paymentRepository,
			RefundRepository refundRepository,
			RefundIdempotencyRepository idempotencyRepository,
			MerchantService merchantService,
			IdempotencyKeyValidator idempotencyKeyValidator,
			SimulatedRefundProcessor refundProcessor,
			LedgerPostingService ledgerPostingService,
			OutboxEventWriter outboxEventWriter,
			JsonMapper jsonMapper) {
		this.paymentRepository = paymentRepository;
		this.refundRepository = refundRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.merchantService = merchantService;
		this.idempotencyKeyValidator = idempotencyKeyValidator;
		this.refundProcessor = refundProcessor;
		this.ledgerPostingService = ledgerPostingService;
		this.outboxEventWriter = outboxEventWriter;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Lock order (deadlock avoidance): payment row → idempotency reservation/row → refund row.
	 * Never acquire an idempotency lock before the parent payment lock on this path.
	 */
	@Transactional
	public RefundResponse create(UUID userId, UUID paymentId, String rawIdempotencyKey, CreateRefundRequest request) {
		String idempotencyKey = idempotencyKeyValidator.requireValid(rawIdempotencyKey);

		PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Payment not found"));
		merchantService.requireOwner(userId, payment.getMerchantId());

		if (payment.getStatus() != PaymentStatus.CAPTURED) {
			throw new ApiException(HttpStatus.CONFLICT, "payment_not_captured",
					"Refunds are only allowed for CAPTURED payments");
		}

		String fingerprint = RefundRequestFingerprint.of(paymentId, request);
		Instant now = Instant.now();
		Instant expiresAt = now.plus(IDEMPOTENCY_RETENTION);

		boolean reserved = idempotencyRepository.tryReserve(
				UUID.randomUUID(),
				payment.getMerchantId(),
				RefundIdempotencyRecordEntity.OPERATION_REFUND_CREATE,
				idempotencyKey,
				fingerprint,
				now,
				expiresAt);

		if (!reserved) {
			RefundIdempotencyRecordEntity existing = idempotencyRepository
					.findForUpdate(
							payment.getMerchantId(),
							RefundIdempotencyRecordEntity.OPERATION_REFUND_CREATE,
							idempotencyKey)
					.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "idempotency_in_progress",
							"Idempotent request is still processing; retry shortly"));
			return handleExisting(existing, fingerprint, now, expiresAt, payment, request);
		}

		RefundIdempotencyRecordEntity record = idempotencyRepository
				.findForUpdate(
						payment.getMerchantId(),
						RefundIdempotencyRecordEntity.OPERATION_REFUND_CREATE,
						idempotencyKey)
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
						"Idempotency reservation missing after insert"));

		return createRefundAndComplete(record, payment, request, now);
	}

	private RefundResponse handleExisting(RefundIdempotencyRecordEntity existing, String fingerprint, Instant now,
			Instant expiresAt, PaymentEntity payment, CreateRefundRequest request) {
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
			return createRefundAndComplete(existing, payment, request, now);
		}

		throw new ApiException(HttpStatus.CONFLICT, "idempotency_in_progress",
				"Idempotent request is still processing; retry shortly");
	}

	private RefundResponse createRefundAndComplete(RefundIdempotencyRecordEntity record, PaymentEntity payment,
			CreateRefundRequest request, Instant now) {
		String merchantReference = request.merchantReference().trim();
		long amountMinor = request.amountMinor();

		long reserved = refundRepository.sumAmountMinorByPaymentIdAndStatusIn(
				payment.getId(), CAPACITY_RESERVING_STATUSES);
		long remaining = payment.getAmountMinor() - reserved;
		if (amountMinor > remaining) {
			throw new ApiException(HttpStatus.CONFLICT, "insufficient_refundable_capacity",
					"Refund amount exceeds remaining refundable capacity");
		}

		// Local/synchronous only — must not hold locks across network I/O.
		RefundResult result = refundProcessor.process(payment, amountMinor, merchantReference);

		RefundEntity refund = RefundEntity.createPending(
				UUID.randomUUID(),
				payment.getId(),
				payment.getMerchantId(),
				merchantReference,
				amountMinor,
				payment.getCurrency(),
				now);

		if (result.approved()) {
			refund.applySucceeded(now);
		}
		else {
			refund.applyFailed(result.failureCode(), now);
		}

		try {
			refundRepository.saveAndFlush(refund);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "conflict",
					"A refund with this merchant reference already exists");
		}

		if (result.approved()) {
			ledgerPostingService.postSuccessfulRefund(refund);
			outboxEventWriter.appendRefundSucceeded(refund);
		}

		RefundResponse response = toResponse(refund);
		String responseJson;
		try {
			responseJson = jsonMapper.writeValueAsString(response);
		}
		catch (RuntimeException ex) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
					"Failed to persist idempotent response");
		}

		record.markCompleted(refund.getId(), HttpStatus.CREATED.value(), responseJson, Instant.now());
		idempotencyRepository.saveAndFlush(record);
		return response;
	}

	@Transactional(readOnly = true)
	public RefundResponse getForOwner(UUID userId, UUID refundId) {
		RefundEntity refund = refundRepository.findById(refundId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Refund not found"));
		merchantService.requireOwner(userId, refund.getMerchantId());
		return toResponse(refund);
	}

	@Transactional(readOnly = true)
	public RefundPageResponse listForPaymentOwner(UUID userId, UUID paymentId, int page, int size) {
		PaymentEntity payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Payment not found"));
		merchantService.requireOwner(userId, payment.getMerchantId());
		validatePage(page, size);

		PageRequest pageable = PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		Page<RefundEntity> result = refundRepository.findByPaymentIdAndMerchantId(
				payment.getId(), payment.getMerchantId(), pageable);
		return new RefundPageResponse(
				result.getContent().stream().map(RefundService::toResponse).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	private static void validatePage(int page, int size) {
		if (page < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "page must be >= 0");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error",
					"size must be between 1 and " + MAX_PAGE_SIZE);
		}
	}

	private RefundResponse readStoredResponse(RefundIdempotencyRecordEntity existing) {
		try {
			return jsonMapper.readValue(existing.getResponseJson(), RefundResponse.class);
		}
		catch (RuntimeException ex) {
			return refundRepository.findById(existing.getRefundId())
					.map(RefundService::toResponse)
					.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
							"Stored idempotent refund is unavailable"));
		}
	}

	private static RefundResponse toResponse(RefundEntity refund) {
		return new RefundResponse(
				refund.getId(),
				refund.getPaymentId(),
				refund.getMerchantId(),
				refund.getMerchantReference(),
				refund.getAmountMinor(),
				refund.getCurrency(),
				refund.getStatus(),
				refund.getFailureCode(),
				refund.getCompletedAt(),
				refund.getCreatedAt(),
				refund.getUpdatedAt());
	}
}

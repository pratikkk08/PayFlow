package com.payflow.outbox.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.outbox.domain.OutboxAggregateType;
import com.payflow.outbox.domain.OutboxEventEntity;
import com.payflow.outbox.domain.OutboxEventType;
import com.payflow.outbox.persistence.OutboxEventRepository;
import com.payflow.payments.domain.PaymentEntity;
import com.payflow.refunds.domain.RefundEntity;
import com.payflow.shared.error.ApiException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Appends PENDING outbox rows in the caller's PostgreSQL transaction.
 * Propagation is REQUIRED (join only) — never {@code REQUIRES_NEW}.
 */
@Service
public class OutboxEventWriter {

	public static final int EVENT_SCHEMA_VERSION = 1;

	private final OutboxEventRepository outboxEventRepository;
	private final JsonMapper jsonMapper;
	private final ObjectProvider<OutboxEventWriteFaultInjector> faultInjector;

	public OutboxEventWriter(OutboxEventRepository outboxEventRepository,
			JsonMapper jsonMapper,
			ObjectProvider<OutboxEventWriteFaultInjector> faultInjector) {
		this.outboxEventRepository = outboxEventRepository;
		this.jsonMapper = jsonMapper;
		this.faultInjector = faultInjector;
	}

	@Transactional
	public void appendPaymentEvent(PaymentEntity payment, OutboxEventType eventType) {
		maybeInjectFault();
		Instant now = Instant.now();
		String payload = toJson(paymentPayload(payment));
		OutboxEventEntity event = OutboxEventEntity.pending(
				UUID.randomUUID(),
				OutboxAggregateType.PAYMENT,
				payment.getId(),
				eventType,
				EVENT_SCHEMA_VERSION,
				payment.getMerchantId(),
				payload,
				now);
		outboxEventRepository.saveAndFlush(event);
	}

	@Transactional
	public void appendRefundSucceeded(RefundEntity refund) {
		maybeInjectFault();
		Instant now = Instant.now();
		String payload = toJson(refundPayload(refund));
		OutboxEventEntity event = OutboxEventEntity.pending(
				UUID.randomUUID(),
				OutboxAggregateType.REFUND,
				refund.getId(),
				OutboxEventType.REFUND_SUCCEEDED,
				EVENT_SCHEMA_VERSION,
				refund.getMerchantId(),
				payload,
				now);
		outboxEventRepository.saveAndFlush(event);
	}

	private static Map<String, Object> paymentPayload(PaymentEntity payment) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("paymentId", payment.getId().toString());
		payload.put("merchantId", payment.getMerchantId().toString());
		payload.put("amountMinor", payment.getAmountMinor());
		payload.put("currency", payment.getCurrency());
		payload.put("status", payment.getStatus().name());
		payload.put("createdAt", payment.getCreatedAt().toString());
		if (payment.getAuthorizedAt() != null) {
			payload.put("authorizedAt", payment.getAuthorizedAt().toString());
		}
		if (payment.getCapturedAt() != null) {
			payload.put("capturedAt", payment.getCapturedAt().toString());
		}
		if (payment.getCancelledAt() != null) {
			payload.put("cancelledAt", payment.getCancelledAt().toString());
		}
		return payload;
	}

	private static Map<String, Object> refundPayload(RefundEntity refund) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("refundId", refund.getId().toString());
		payload.put("paymentId", refund.getPaymentId().toString());
		payload.put("merchantId", refund.getMerchantId().toString());
		payload.put("amountMinor", refund.getAmountMinor());
		payload.put("currency", refund.getCurrency());
		payload.put("status", refund.getStatus().name());
		payload.put("createdAt", refund.getCreatedAt().toString());
		if (refund.getCompletedAt() != null) {
			payload.put("completedAt", refund.getCompletedAt().toString());
		}
		return payload;
	}

	private String toJson(Map<String, Object> payload) {
		try {
			return jsonMapper.writeValueAsString(payload);
		}
		catch (RuntimeException ex) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "outbox_write_failed",
					"Failed to serialize outbox event payload");
		}
	}

	private void maybeInjectFault() {
		OutboxEventWriteFaultInjector injector = faultInjector.getIfAvailable();
		if (injector != null) {
			injector.beforeWrite();
		}
	}
}

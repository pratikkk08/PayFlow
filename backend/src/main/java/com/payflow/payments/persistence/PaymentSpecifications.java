package com.payflow.payments.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.payflow.payments.domain.PaymentEntity;
import com.payflow.payments.domain.PaymentStatus;

import jakarta.persistence.criteria.Predicate;

public final class PaymentSpecifications {

	private PaymentSpecifications() {
	}

	public static Specification<PaymentEntity> filtered(UUID merchantId, PaymentStatus status, Instant createdFrom,
			Instant createdTo) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("merchantId"), merchantId));
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (createdFrom != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
			}
			if (createdTo != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}
}

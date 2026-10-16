package com.payflow.refunds.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import com.payflow.refunds.api.dto.CreateRefundRequest;

final class RefundRequestFingerprint {

	private RefundRequestFingerprint() {
	}

	static String of(UUID paymentId, CreateRefundRequest request) {
		String reason = request.reason() == null || request.reason().isBlank()
				? ""
				: request.reason().trim();
		String canonical = String.join("|",
				paymentId.toString(),
				request.merchantReference().trim(),
				Long.toString(request.amountMinor()),
				reason);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}
}

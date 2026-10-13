package com.payflow.payments.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.payflow.payments.api.dto.CreatePaymentRequest;

final class PaymentRequestFingerprint {

	private PaymentRequestFingerprint() {
	}

	static String of(CreatePaymentRequest request) {
		String description = request.description() == null || request.description().isBlank()
				? ""
				: request.description().trim();
		String canonical = String.join("|",
				request.merchantId().toString(),
				request.merchantReference().trim(),
				Long.toString(request.amountMinor()),
				request.currency(),
				description);
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

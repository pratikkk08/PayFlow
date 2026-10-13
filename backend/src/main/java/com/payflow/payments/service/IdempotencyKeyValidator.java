package com.payflow.payments.service;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.payflow.shared.error.ApiException;

@Component
public class IdempotencyKeyValidator {

	private static final int MIN_LENGTH = 8;
	private static final int MAX_LENGTH = 128;
	private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9._~:-]{8,128}$");

	public String requireValid(String rawKey) {
		if (rawKey == null || rawKey.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error",
					"Idempotency-Key header is required");
		}
		String key = rawKey.trim();
		if (key.length() < MIN_LENGTH || key.length() > MAX_LENGTH || !ALLOWED.matcher(key).matches()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error",
					"Idempotency-Key must be 8-128 characters of [A-Za-z0-9._~:-]");
		}
		return key;
	}
}

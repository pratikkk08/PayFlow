package com.payflow.shared.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		List<FieldViolation> violations) {

	public record FieldViolation(String field, String message) {
	}

	public static ApiError of(int status, String error, String message, String path) {
		return new ApiError(Instant.now(), status, error, message, path, List.of());
	}

	public static ApiError of(int status, String error, String message, String path, List<FieldViolation> violations) {
		return new ApiError(Instant.now(), status, error, message, path, violations);
	}
}

package com.payflow.identity.api.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String fullName,
		String email,
		Set<String> roles,
		Instant createdAt) {
}

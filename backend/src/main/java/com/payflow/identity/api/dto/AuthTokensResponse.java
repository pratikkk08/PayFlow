package com.payflow.identity.api.dto;

import java.util.UUID;

public record AuthTokensResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresInSeconds,
		UUID userId) {
}

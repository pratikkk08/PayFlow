package com.payflow.identity.api.dto;

import java.util.UUID;

/**
 * Browser-session login/refresh response. Refresh token is delivered only via HttpOnly cookie.
 */
public record BrowserAccessTokenResponse(
		String accessToken,
		String tokenType,
		long expiresInSeconds,
		UUID userId) {

	public static BrowserAccessTokenResponse from(AuthTokensResponse tokens) {
		return new BrowserAccessTokenResponse(
				tokens.accessToken(),
				tokens.tokenType(),
				tokens.expiresInSeconds(),
				tokens.userId());
	}
}

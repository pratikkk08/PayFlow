package com.payflow.shared.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves JWT bearer tokens only. Merchant API keys ({@code sk_test_}/{@code pk_test_})
 * are handled by {@link ApiKeyAuthenticationFilter} and must not be parsed as JWTs.
 */
@Component
public class PayflowBearerTokenResolver implements BearerTokenResolver {

	@Override
	public String resolve(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return null;
		}
		String token = header.substring(7).trim();
		if (token.isEmpty()) {
			return null;
		}
		if (token.startsWith("sk_test_") || token.startsWith("pk_test_")
				|| token.startsWith("sk_live_") || token.startsWith("pk_live_")) {
			return null;
		}
		return token;
	}
}

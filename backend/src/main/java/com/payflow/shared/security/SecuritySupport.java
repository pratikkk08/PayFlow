package com.payflow.shared.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.payflow.shared.error.ApiException;

@Component
public class SecuritySupport {

	public UUID requireUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuth) {
			Jwt jwt = jwtAuth.getToken();
			Object tokenUse = jwt.getClaim(JwtTokenService.CLAIM_TOKEN_USE);
			if (tokenUse != null && !JwtTokenService.TOKEN_USE_ACCESS.equals(String.valueOf(tokenUse))) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid access token");
			}
			return UUID.fromString(jwt.getSubject());
		}
		if (authentication instanceof ApiKeyAuthenticationToken) {
			throw new ApiException(HttpStatus.FORBIDDEN, "forbidden",
					"Merchant API keys cannot access user account endpoints");
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "User authentication required");
	}

	public ApiKeyAuthenticationToken requireApiKeyAuth() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof ApiKeyAuthenticationToken apiKeyAuth) {
			return apiKeyAuth;
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Merchant secret API key required");
	}

	public boolean isJwtAuth() {
		return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken;
	}

	public boolean isApiKeyAuth() {
		return SecurityContextHolder.getContext().getAuthentication() instanceof ApiKeyAuthenticationToken;
	}
}

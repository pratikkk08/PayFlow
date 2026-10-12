package com.payflow.shared.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.payflow.merchants.service.ApiKeyAuthenticationService;
import com.payflow.shared.error.ApiError;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authenticates {@code Authorization: Bearer sk_test_...} credentials.
 * JWT Bearer tokens are ignored here and handled by the OAuth2 resource server.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	private final ApiKeyAuthenticationService apiKeyAuthenticationService;
	private final JsonMapper jsonMapper;

	public ApiKeyAuthenticationFilter(ApiKeyAuthenticationService apiKeyAuthenticationService,
			JsonMapper jsonMapper) {
		this.apiKeyAuthenticationService = apiKeyAuthenticationService;
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = header.substring("Bearer ".length()).trim();
		if (!apiKeyAuthenticationService.looksLikeSecretApiKey(token)) {
			if (apiKeyAuthenticationService.looksLikePublishableApiKey(token)) {
				writeUnauthorized(response, request.getRequestURI(), "Publishable keys cannot authenticate");
				return;
			}
			filterChain.doFilter(request, response);
			return;
		}

		Optional<ApiKeyAuthenticationToken> authenticated = apiKeyAuthenticationService.authenticate(token);
		if (authenticated.isEmpty()) {
			writeUnauthorized(response, request.getRequestURI(), "Invalid or revoked API key");
			return;
		}

		SecurityContextHolder.getContext().setAuthentication(authenticated.get());
		filterChain.doFilter(request, response);
	}

	private void writeUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), ApiError.of(401, "unauthorized", message, path));
	}
}

package com.payflow.shared.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Propagates or generates a request correlation id for logs and the {@code X-Request-Id} response
 * header. Never reads Authorization or Cookie headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "correlationId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String incoming = request.getHeader(HEADER);
		String correlationId = sanitize(incoming);
		if (correlationId == null) {
			correlationId = UUID.randomUUID().toString();
		}
		MDC.put(MDC_KEY, correlationId);
		response.setHeader(HEADER, correlationId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	/**
	 * Accepts UUID-like or short alphanumeric tokens only. Rejects oversized or suspicious values.
	 */
	static String sanitize(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		if (trimmed.isEmpty() || trimmed.length() > 64) {
			return null;
		}
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
				continue;
			}
			return null;
		}
		return trimmed;
	}
}

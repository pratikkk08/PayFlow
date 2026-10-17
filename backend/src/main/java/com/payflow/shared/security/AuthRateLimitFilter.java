package com.payflow.shared.security;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.payflow.shared.error.ApiError;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

	private final StringRedisTemplate redisTemplate;
	private final PayflowSecurityProperties properties;
	private final JsonMapper jsonMapper;

	public AuthRateLimitFilter(StringRedisTemplate redisTemplate, PayflowSecurityProperties properties,
			JsonMapper jsonMapper) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!properties.getRateLimit().isEnabled()) {
			return true;
		}
		String path = request.getRequestURI();
		return !(path.equals("/api/v1/auth/login")
				|| path.equals("/api/v1/auth/register")
				|| path.equals("/api/v1/auth/refresh")
				|| path.equals("/api/v1/auth/browser/login")
				|| path.equals("/api/v1/auth/browser/refresh"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String clientKey = resolveClientKey(request);
		String redisKey = "payflow:ratelimit:auth:" + clientKey;
		try {
			Long count = redisTemplate.opsForValue().increment(redisKey);
			if (count != null && count == 1L) {
				redisTemplate.expire(redisKey, Duration.ofMinutes(1));
			}
			if (count != null && count > properties.getRateLimit().getAuthRequestsPerMinute()) {
				writeError(response, request.getRequestURI(), 429, "rate_limited", "Too many authentication attempts");
				return;
			}
		}
		catch (Exception ex) {
			log.error("Redis rate-limit check failed for auth endpoint", ex);
			if (properties.getRateLimit().isFailClosedWhenRedisUnavailable()) {
				writeError(response, request.getRequestURI(), 503, "rate_limit_unavailable",
						"Authentication temporarily unavailable");
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	private String resolveClientKey(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
	}

	private void writeError(HttpServletResponse response, String path, int status, String error, String message)
			throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), ApiError.of(status, error, message, path));
	}
}

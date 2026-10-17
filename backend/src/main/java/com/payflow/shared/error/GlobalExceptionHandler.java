package com.payflow.shared.error;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.payflow.shared.web.RequestCorrelationFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
		return ResponseEntity.status(ex.getStatus())
				.body(ApiError.of(ex.getStatus().value(), ex.getError(), ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
				.map(this::toViolation)
				.toList();
		return ResponseEntity.badRequest()
				.body(ApiError.of(400, "validation_error", "Request validation failed", request.getRequestURI(),
						violations));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class })
	public ResponseEntity<ApiError> handleUnreadable(Exception ex, HttpServletRequest request) {
		return ResponseEntity.badRequest()
				.body(ApiError.of(400, "invalid_request", "Malformed request body", request.getRequestURI()));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
			HttpServletRequest request) {
		return ResponseEntity.badRequest()
				.body(ApiError.of(400, "validation_error",
						"Missing required parameter: " + ex.getParameterName(), request.getRequestURI()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return ResponseEntity.badRequest()
				.body(ApiError.of(400, "validation_error",
						"Invalid value for parameter: " + ex.getName(), request.getRequestURI()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
		log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiError.of(409, "conflict", "Resource conflict", request.getRequestURI()));
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiError.of(409, "conflict", "Payment was updated concurrently; retry with current state",
						request.getRequestURI()));
	}

	@ExceptionHandler({ BadCredentialsException.class, AuthenticationException.class })
	public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest request) {
		// Path + exception type only — never credentials, tokens, or Authorization/Cookie headers.
		log.warn("auth_failure path={} type={}", request.getRequestURI(), ex.getClass().getSimpleName());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ApiError.of(401, "unauthorized", "Authentication failed", request.getRequestURI()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		log.warn("access_denied path={}", request.getRequestURI());
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiError.of(403, "forbidden", "Access denied", request.getRequestURI()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex, WebRequest request) {
		String path = request instanceof ServletWebRequest servlet
				? servlet.getRequest().getRequestURI()
				: "";
		String errorId = UUID.randomUUID().toString();
		String correlationId = MDC.get(RequestCorrelationFilter.MDC_KEY);
		log.error("Unhandled error errorId={} correlationId={} path={}", errorId, correlationId, path, ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiError.of(500, "internal_error", "An unexpected error occurred", path));
	}

	private ApiError.FieldViolation toViolation(FieldError error) {
		return new ApiError.FieldViolation(error.getField(),
				error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage());
	}
}

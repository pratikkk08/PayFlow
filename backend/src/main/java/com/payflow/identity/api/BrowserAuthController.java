package com.payflow.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.identity.api.dto.AuthTokensResponse;
import com.payflow.identity.api.dto.BrowserAccessTokenResponse;
import com.payflow.identity.api.dto.LoginRequest;
import com.payflow.identity.service.AuthService;
import com.payflow.identity.service.RefreshCookieService;
import com.payflow.shared.error.ApiException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Browser SPA session endpoints: access JWT in JSON body, refresh in HttpOnly cookie.
 * Existing JSON-body {@code /api/v1/auth/login|refresh|logout} APIs are unchanged.
 */
@RestController
@RequestMapping("/api/v1/auth/browser")
@Tag(name = "Browser Authentication")
public class BrowserAuthController {

	private final AuthService authService;
	private final RefreshCookieService refreshCookieService;

	public BrowserAuthController(AuthService authService, RefreshCookieService refreshCookieService) {
		this.authService = authService;
		this.refreshCookieService = refreshCookieService;
	}

	@GetMapping("/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Ensure CSRF cookie is issued for browser session POSTs")
	public void csrf(CsrfToken csrfToken) {
		// Accessing the token forces CookieCsrfTokenRepository to write XSRF-TOKEN.
		csrfToken.getToken();
	}

	@PostMapping("/login")
	@Operation(summary = "Browser login: access token in body, refresh in HttpOnly cookie")
	public BrowserAccessTokenResponse login(
			@Valid @RequestBody LoginRequest request,
			HttpServletResponse response) {
		AuthTokensResponse tokens = authService.login(request);
		refreshCookieService.writeRefreshCookie(response, tokens.refreshToken());
		return BrowserAccessTokenResponse.from(tokens);
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate refresh cookie and issue a new access token")
	public BrowserAccessTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
		String rawRefresh = refreshCookieService.readRefreshCookie(request);
		if (rawRefresh == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Missing refresh session");
		}
		try {
			AuthTokensResponse tokens = authService.refresh(rawRefresh);
			refreshCookieService.writeRefreshCookie(response, tokens.refreshToken());
			return BrowserAccessTokenResponse.from(tokens);
		}
		catch (ApiException ex) {
			refreshCookieService.clearRefreshCookie(response);
			throw ex;
		}
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoke refresh family and clear refresh cookie")
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		String rawRefresh = refreshCookieService.readRefreshCookie(request);
		if (rawRefresh != null) {
			authService.logout(rawRefresh);
		}
		refreshCookieService.clearRefreshCookie(response);
	}
}

package com.payflow.identity.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.payflow.shared.security.PayflowSecurityProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sets and clears the HttpOnly refresh cookie used by browser-session auth endpoints.
 * Never logs cookie values.
 */
@Service
public class RefreshCookieService {

	private final PayflowSecurityProperties securityProperties;

	public RefreshCookieService(PayflowSecurityProperties securityProperties) {
		this.securityProperties = securityProperties;
	}

	public void writeRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
		PayflowSecurityProperties.BrowserSession browser = securityProperties.getBrowserSession();
		ResponseCookie cookie = ResponseCookie.from(browser.getRefreshCookieName(), rawRefreshToken)
				.httpOnly(true)
				.secure(browser.isCookieSecure())
				.path(browser.getRefreshCookiePath())
				.maxAge(securityProperties.getRefreshToken().getTtl())
				.sameSite(browser.getCookieSameSite())
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void clearRefreshCookie(HttpServletResponse response) {
		PayflowSecurityProperties.BrowserSession browser = securityProperties.getBrowserSession();
		ResponseCookie cookie = ResponseCookie.from(browser.getRefreshCookieName(), "")
				.httpOnly(true)
				.secure(browser.isCookieSecure())
				.path(browser.getRefreshCookiePath())
				.maxAge(0)
				.sameSite(browser.getCookieSameSite())
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public String readRefreshCookie(HttpServletRequest request) {
		String name = securityProperties.getBrowserSession().getRefreshCookieName();
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				String value = cookie.getValue();
				return (value == null || value.isBlank()) ? null : value;
			}
		}
		return null;
	}
}

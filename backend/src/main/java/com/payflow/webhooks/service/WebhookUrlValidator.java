package com.payflow.webhooks.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.payflow.shared.error.ApiException;

/**
 * Registration-time webhook URL validation (no network I/O).
 *
 * <p>Delivery-time checks (DNS resolution of all addresses, redirect refusal, per-attempt
 * revalidation) live in {@link WebhookSsrfGuard} and must be applied by the future worker.
 */
@Component
public class WebhookUrlValidator {

	public String validateForRegistration(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			throw invalid("Webhook URL is required");
		}
		String trimmed = rawUrl.trim();
		if (trimmed.length() > 2048) {
			throw invalid("Webhook URL must be at most 2048 characters");
		}

		URI uri;
		try {
			uri = new URI(trimmed);
		}
		catch (URISyntaxException ex) {
			throw invalid("Webhook URL is malformed");
		}

		if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
			throw invalid("Webhook URL must use HTTPS");
		}
		if (uri.getRawUserInfo() != null) {
			throw invalid("Webhook URL must not contain credentials");
		}
		if (uri.getFragment() != null || trimmed.contains("#")) {
			throw invalid("Webhook URL must not contain a fragment");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw invalid("Webhook URL must include a host");
		}

		String host = uri.getHost().toLowerCase(Locale.ROOT);
		if (WebhookSsrfGuard.isBlockedHostname(host)) {
			throw invalid("Webhook URL host is not allowed");
		}
		if (isIpLiteral(host)) {
			throw invalid("Webhook URL must not use an IP literal");
		}

		// Normalize to a canonical ASCII form without userinfo/fragment.
		try {
			URI normalized = new URI("https", null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), null);
			return normalized.toString();
		}
		catch (URISyntaxException ex) {
			throw invalid("Webhook URL is malformed");
		}
	}

	/**
	 * Future delivery hook: resolve host and ensure every address is allowed.
	 * Not used during registration (no network contact in Task 5.5).
	 */
	public void validateResolvedHostForDelivery(String host) throws UnknownHostException {
		if (WebhookSsrfGuard.isBlockedHostname(host) || isIpLiteral(host)) {
			throw invalid("Webhook URL host is not allowed for delivery");
		}
		InetAddress[] addresses = InetAddress.getAllByName(host);
		if (addresses.length == 0) {
			throw invalid("Webhook URL host could not be resolved");
		}
		for (InetAddress address : addresses) {
			if (WebhookSsrfGuard.isBlockedAddress(address)) {
				throw invalid("Webhook URL resolves to a blocked address");
			}
		}
	}

	/** Structural check only — never performs DNS resolution. */
	private static boolean isIpLiteral(String host) {
		if (host.startsWith("[") && host.endsWith("]")) {
			return true; // URI IPv6 literal
		}
		if (isDottedDecimal(host)) {
			return true;
		}
		// Unbracketed IPv6 or other colon forms are not valid webhook hosts here.
		return host.indexOf(':') >= 0;
	}

	private static boolean isDottedDecimal(String host) {
		String[] parts = host.split("\\.");
		if (parts.length != 4) {
			return false;
		}
		for (String part : parts) {
			try {
				int value = Integer.parseInt(part);
				if (value < 0 || value > 255) {
					return false;
				}
			}
			catch (NumberFormatException ex) {
				return false;
			}
		}
		return true;
	}

	private static ApiException invalid(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "invalid_webhook_url", message);
	}
}

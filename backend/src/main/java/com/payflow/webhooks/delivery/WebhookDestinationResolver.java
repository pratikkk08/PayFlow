package com.payflow.webhooks.delivery;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.payflow.webhooks.service.WebhookSsrfGuard;

/**
 * Resolves and validates webhook destinations immediately before each delivery attempt.
 * Fail-closed: any blocked hostname/address or empty resolution aborts delivery.
 */
@Component
public class WebhookDestinationResolver {

	private final WebhookDnsResolver dnsResolver;

	public WebhookDestinationResolver(WebhookDnsResolver dnsResolver) {
		this.dnsResolver = dnsResolver;
	}

	public WebhookDestination resolve(String rawUrl) {
		URI uri;
		try {
			uri = new URI(rawUrl);
		}
		catch (URISyntaxException ex) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.PERMANENT, "invalid_url", null, ex);
		}
		if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.PERMANENT, "url_not_https", null);
		}
		if (uri.getRawUserInfo() != null || uri.getFragment() != null) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.PERMANENT, "url_unsafe_components", null);
		}
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.PERMANENT, "url_missing_host", null);
		}
		host = host.toLowerCase(Locale.ROOT);
		if (WebhookSsrfGuard.isBlockedHostname(host) || looksLikeIpLiteral(host)) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.PERMANENT, "host_blocked", null);
		}

		InetAddress[] resolved;
		try {
			resolved = dnsResolver.resolve(host);
		}
		catch (UnknownHostException ex) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.RETRYABLE, "dns_resolve_failed", null, ex);
		}
		if (resolved == null || resolved.length == 0) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.RETRYABLE, "dns_empty", null);
		}

		List<InetAddress> allowed = new ArrayList<>();
		for (InetAddress address : resolved) {
			if (WebhookSsrfGuard.isBlockedAddress(address)) {
				continue;
			}
			allowed.add(address);
		}
		if (allowed.isEmpty()) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.PERMANENT, "all_resolved_addresses_blocked",
					null);
		}

		int port = uri.getPort() > 0 ? uri.getPort() : 443;
		String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
		String pathAndQuery = uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
		return new WebhookDestination(host, port, pathAndQuery, allowed.getFirst());
	}

	private static boolean looksLikeIpLiteral(String host) {
		if (host.startsWith("[") && host.endsWith("]")) {
			return true;
		}
		if (host.indexOf(':') >= 0) {
			return true;
		}
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
}

package com.payflow.webhooks.service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Set;

/**
 * Address / hostname SSRF denylist for webhook delivery.
 *
 * <p><strong>Important:</strong> Registration-time hostname checks are incomplete.
 * Delivery workers must resolve DNS on every attempt, run {@link #isBlockedAddress(InetAddress)}
 * on <em>every</em> resolved address, refuse redirects, and re-load the endpoint from Postgres
 * before each attempt. Registration validation alone cannot make outbound delivery safe.
 */
public final class WebhookSsrfGuard {

	private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
			"localhost",
			"metadata.google.internal",
			"metadata.goog",
			"kubernetes.default",
			"kubernetes.default.svc");

	private WebhookSsrfGuard() {
	}

	public static boolean isBlockedHostname(String host) {
		if (host == null || host.isBlank()) {
			return true;
		}
		String normalized = host.trim().toLowerCase(Locale.ROOT);
		if (normalized.endsWith(".")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (BLOCKED_HOSTNAMES.contains(normalized)) {
			return true;
		}
		if (normalized.endsWith(".localhost") || normalized.endsWith(".local")
				|| normalized.endsWith(".internal") || normalized.endsWith(".intranet")) {
			return true;
		}
		return false;
	}

	/**
	 * @return true if the address must never be contacted for webhook delivery
	 */
	public static boolean isBlockedAddress(InetAddress address) {
		if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
				|| address.isLinkLocalAddress() || address.isSiteLocalAddress()
				|| address.isMulticastAddress()) {
			return true;
		}
		if (address instanceof Inet4Address ipv4) {
			return isBlockedIpv4(ipv4.getAddress());
		}
		if (address instanceof Inet6Address ipv6) {
			return isBlockedIpv6(ipv6);
		}
		return true;
	}

	private static boolean isBlockedIpv4(byte[] octets) {
		int a = octets[0] & 0xFF;
		int b = octets[1] & 0xFF;
		// 0.0.0.0/8
		if (a == 0) {
			return true;
		}
		// 10.0.0.0/8
		if (a == 10) {
			return true;
		}
		// 100.64.0.0/10 CGNAT
		if (a == 100 && b >= 64 && b <= 127) {
			return true;
		}
		// 127.0.0.0/8
		if (a == 127) {
			return true;
		}
		// 169.254.0.0/16 link-local + cloud metadata 169.254.169.254
		if (a == 169 && b == 254) {
			return true;
		}
		// 172.16.0.0/12
		if (a == 172 && b >= 16 && b <= 31) {
			return true;
		}
		// 192.0.0.0/24, 192.0.2.0/24, 192.168.0.0/16, 198.18.0.0/15, 198.51.100.0/24, 203.0.113.0/24
		if (a == 192 && b == 0) {
			return true;
		}
		if (a == 192 && b == 168) {
			return true;
		}
		if (a == 198 && (b == 18 || b == 19 || b == 51)) {
			return true;
		}
		if (a == 203 && b == 0) {
			return true;
		}
		// 224.0.0.0/4 multicast and above (incl. broadcast)
		if (a >= 224) {
			return true;
		}
		return false;
	}

	private static boolean isBlockedIpv6(Inet6Address address) {
		byte[] bytes = address.getAddress();
		// ::/128 and ::1 already covered by isAnyLocal/isLoopback; unique-local fc00::/7
		int first = bytes[0] & 0xFF;
		if ((first & 0xFE) == 0xFC) {
			return true;
		}
		// IPv4-mapped — inspect embedded v4
		if (address.isIPv4CompatibleAddress() || isIpv4Mapped(bytes)) {
			byte[] v4 = new byte[] { bytes[12], bytes[13], bytes[14], bytes[15] };
			return isBlockedIpv4(v4);
		}
		return false;
	}

	private static boolean isIpv4Mapped(byte[] bytes) {
		for (int i = 0; i < 10; i++) {
			if (bytes[i] != 0) {
				return false;
			}
		}
		return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
	}
}

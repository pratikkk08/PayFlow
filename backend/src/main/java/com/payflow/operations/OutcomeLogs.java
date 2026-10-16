package com.payflow.operations;

/**
 * Single-line log reasons with secret-shaped tokens removed.
 */
public final class OutcomeLogs {

	private OutcomeLogs() {
	}

	public static String reason(String raw) {
		if (raw == null || raw.isBlank()) {
			return "unknown";
		}
		String sanitized = raw.replaceAll("[\\r\\n\\t]+", " ").trim();
		sanitized = sanitized.replaceAll("(?i)whsec_[A-Za-z0-9_\\-]+", "[redacted]");
		sanitized = sanitized.replaceAll("(?i)sk_(test|live)_[A-Za-z0-9_\\-]+", "[redacted]");
		sanitized = sanitized.replaceAll("(?i)bearer\\s+\\S+", "bearer [redacted]");
		return sanitized.length() <= 200 ? sanitized : sanitized.substring(0, 200);
	}
}

package com.payflow.shared.security;

import java.security.SecureRandom;
import java.util.Base64;

public final class TokenGenerator {

	private static final SecureRandom RANDOM = new SecureRandom();

	private TokenGenerator() {
	}

	public static String opaqueToken(int byteLength) {
		byte[] bytes = new byte[byteLength];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String apiKey(String prefix, int randomBytes) {
		return prefix + opaqueToken(randomBytes);
	}
}

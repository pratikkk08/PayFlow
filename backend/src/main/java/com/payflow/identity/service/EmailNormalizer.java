package com.payflow.identity.service;

public final class EmailNormalizer {

	private EmailNormalizer() {
	}

	public static String normalize(String email) {
		return email == null ? null : email.trim().toLowerCase();
	}
}

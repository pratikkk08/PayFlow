package com.payflow.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

	private final UUID merchantId;
	private final UUID apiKeyId;
	private final String keyPrefix;
	private final String keyType;

	public ApiKeyAuthenticationToken(UUID merchantId, UUID apiKeyId, String keyPrefix, String keyType) {
		super(defaultAuthorities());
		this.merchantId = merchantId;
		this.apiKeyId = apiKeyId;
		this.keyPrefix = keyPrefix;
		this.keyType = keyType;
		setAuthenticated(true);
	}

	private static Collection<? extends GrantedAuthority> defaultAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_MERCHANT_API"));
	}

	@Override
	public Object getCredentials() {
		return "";
	}

	@Override
	public Object getPrincipal() {
		return merchantId;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public UUID getApiKeyId() {
		return apiKeyId;
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public String getKeyType() {
		return keyType;
	}
}

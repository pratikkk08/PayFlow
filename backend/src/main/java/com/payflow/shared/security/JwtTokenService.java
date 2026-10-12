package com.payflow.shared.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

	public static final String CLAIM_TOKEN_USE = "token_use";
	public static final String CLAIM_ROLES = "roles";
	public static final String TOKEN_USE_ACCESS = "access";

	private final JwtEncoder jwtEncoder;
	private final PayflowSecurityProperties properties;

	public JwtTokenService(JwtEncoder jwtEncoder, PayflowSecurityProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	public String createAccessToken(UUID userId, Collection<String> roles) {
		Instant now = Instant.now();
		Instant expires = now.plus(properties.getJwt().getAccessTokenTtl());
		List<String> roleList = roles.stream().sorted().collect(Collectors.toList());

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.getJwt().getIssuer())
				.audience(List.of(properties.getJwt().getAudience()))
				.issuedAt(now)
				.expiresAt(expires)
				.subject(userId.toString())
				.claim(CLAIM_TOKEN_USE, TOKEN_USE_ACCESS)
				.claim(CLAIM_ROLES, roleList)
				.build();

		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
		return jwt.getTokenValue();
	}
}

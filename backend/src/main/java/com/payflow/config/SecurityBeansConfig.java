package com.payflow.config;

import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.payflow.shared.security.JwtTokenService;
import com.payflow.shared.security.PayflowSecurityProperties;
import com.payflow.shared.security.RsaKeyService;

@Configuration
@EnableConfigurationProperties(PayflowSecurityProperties.class)
public class SecurityBeansConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtAuthenticationConverter payflowJwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(JwtTokenService.CLAIM_ROLES);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	JwtEncoder jwtEncoder(RsaKeyService rsaKeyService) {
		RSAKey rsaKey = new RSAKey.Builder(rsaKeyService.getPublicKey())
				.privateKey(rsaKeyService.getPrivateKey())
				.keyID(UUID.randomUUID().toString())
				.build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
	}

	@Bean
	JwtDecoder jwtDecoder(RsaKeyService rsaKeyService, PayflowSecurityProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKeyService.getPublicKey()).build();

		OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer());
		OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
			if (jwt.getAudience() != null && jwt.getAudience().contains(properties.getJwt().getAudience())) {
				return OAuth2TokenValidatorResult.success();
			}
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error("invalid_token", "Invalid audience", null));
		};
		OAuth2TokenValidator<Jwt> tokenUseValidator = jwt -> {
			Object tokenUse = jwt.getClaim(JwtTokenService.CLAIM_TOKEN_USE);
			if (JwtTokenService.TOKEN_USE_ACCESS.equals(String.valueOf(tokenUse))) {
				return OAuth2TokenValidatorResult.success();
			}
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error("invalid_token", "Invalid token_use", null));
		};

		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				withIssuer, audienceValidator, tokenUseValidator));
		return decoder;
	}
}

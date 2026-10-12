package com.payflow.identity.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.identity.api.dto.AuthTokensResponse;
import com.payflow.identity.api.dto.LoginRequest;
import com.payflow.identity.api.dto.RegisterRequest;
import com.payflow.identity.api.dto.UserResponse;
import com.payflow.identity.domain.PlatformRole;
import com.payflow.identity.domain.RefreshTokenEntity;
import com.payflow.identity.domain.RefreshTokenFamilyEntity;
import com.payflow.identity.domain.UserEntity;
import com.payflow.identity.persistence.RefreshTokenFamilyRepository;
import com.payflow.identity.persistence.RefreshTokenRepository;
import com.payflow.identity.persistence.UserRepository;
import com.payflow.shared.error.ApiException;
import com.payflow.shared.security.JwtTokenService;
import com.payflow.shared.security.PayflowSecurityProperties;
import com.payflow.shared.security.SecurityHashes;
import com.payflow.shared.security.TokenGenerator;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final RefreshTokenFamilyRepository refreshTokenFamilyRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final PayflowSecurityProperties securityProperties;
	private final SecurityAuditService securityAuditService;

	public AuthService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			RefreshTokenFamilyRepository refreshTokenFamilyRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService,
			PayflowSecurityProperties securityProperties,
			SecurityAuditService securityAuditService) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenFamilyRepository = refreshTokenFamilyRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.securityProperties = securityProperties;
		this.securityAuditService = securityAuditService;
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		String normalized = EmailNormalizer.normalize(request.email());
		if (userRepository.existsByEmailNormalized(normalized)) {
			throw new ApiException(HttpStatus.CONFLICT, "email_taken", "Email is already registered");
		}

		Instant now = Instant.now();
		UserEntity user = new UserEntity(
				UUID.randomUUID(),
				request.fullName().trim(),
				request.email().trim(),
				normalized,
				passwordEncoder.encode(request.password()),
				now,
				Set.of(PlatformRole.USER));

		try {
			userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "email_taken", "Email is already registered");
		}

		securityAuditService.record("USER_REGISTERED", user.getId(), null, null, "user registered", null);
		log.info("Registered user id={}", user.getId());
		return toUserResponse(user);
	}

	@Transactional
	public AuthTokensResponse login(LoginRequest request) {
		String normalized = EmailNormalizer.normalize(request.email());
		UserEntity user = userRepository.findByEmailNormalized(normalized)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials",
						"Invalid email or password"));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			securityAuditService.record("LOGIN_FAILED", user.getId(), null, null, "bad password", null);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid email or password");
		}

		AuthTokensResponse tokens = issueTokens(user);
		securityAuditService.record("LOGIN_SUCCESS", user.getId(), null, null, "login", null);
		return tokens;
	}

	@Transactional
	public AuthTokensResponse refresh(String rawRefreshToken) {
		String hash = SecurityHashes.sha256Hex(rawRefreshToken);
		RefreshTokenEntity existing = refreshTokenRepository.findByTokenHashForUpdate(hash)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token",
						"Invalid refresh token"));

		RefreshTokenFamilyEntity family = refreshTokenFamilyRepository.findById(existing.getFamilyId())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token",
						"Invalid refresh token"));

		Instant now = Instant.now();
		if (family.isRevoked()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Refresh session revoked");
		}

		if (existing.isConsumed()) {
			family.revoke(now, "REFRESH_REUSE");
			refreshTokenFamilyRepository.save(family);
			securityAuditService.record("REFRESH_REUSE", existing.getUserId(), null, null,
					"refresh token reuse detected", null);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token",
					"Refresh token reuse detected; session revoked");
		}

		if (existing.isExpired(now)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Refresh token expired");
		}

		UserEntity user = userRepository.findById(existing.getUserId())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token",
						"Invalid refresh token"));

		String newRaw = TokenGenerator.opaqueToken(32);
		UUID newId = UUID.randomUUID();
		RefreshTokenEntity replacement = new RefreshTokenEntity(
				newId,
				family.getId(),
				user.getId(),
				SecurityHashes.sha256Hex(newRaw),
				now.plus(securityProperties.getRefreshToken().getTtl()),
				now);
		refreshTokenRepository.save(replacement);
		existing.consume(now, newId);
		refreshTokenRepository.save(existing);

		String accessToken = jwtTokenService.createAccessToken(user.getId(), roleNames(user));
		return new AuthTokensResponse(
				accessToken,
				newRaw,
				"Bearer",
				securityProperties.getJwt().getAccessTokenTtl().toSeconds(),
				user.getId());
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		String hash = SecurityHashes.sha256Hex(rawRefreshToken);
		refreshTokenRepository.findByTokenHashForUpdate(hash).ifPresent(token -> {
			RefreshTokenFamilyEntity family = refreshTokenFamilyRepository.findById(token.getFamilyId())
					.orElse(null);
			if (family != null && !family.isRevoked()) {
				family.revoke(Instant.now(), "LOGOUT");
				refreshTokenFamilyRepository.save(family);
				securityAuditService.record("LOGOUT", token.getUserId(), null, null, "logout", null);
			}
			if (!token.isConsumed()) {
				token.consume(Instant.now(), null);
				refreshTokenRepository.save(token);
			}
		});
	}

	@Transactional(readOnly = true)
	public UserResponse getMe(UUID userId) {
		UserEntity user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "User not found"));
		return toUserResponse(user);
	}

	private AuthTokensResponse issueTokens(UserEntity user) {
		Instant now = Instant.now();
		RefreshTokenFamilyEntity family = new RefreshTokenFamilyEntity(UUID.randomUUID(), user.getId(), now);
		refreshTokenFamilyRepository.save(family);

		String rawRefresh = TokenGenerator.opaqueToken(32);
		RefreshTokenEntity refreshToken = new RefreshTokenEntity(
				UUID.randomUUID(),
				family.getId(),
				user.getId(),
				SecurityHashes.sha256Hex(rawRefresh),
				now.plus(securityProperties.getRefreshToken().getTtl()),
				now);
		refreshTokenRepository.save(refreshToken);

		String accessToken = jwtTokenService.createAccessToken(user.getId(), roleNames(user));
		return new AuthTokensResponse(
				accessToken,
				rawRefresh,
				"Bearer",
				securityProperties.getJwt().getAccessTokenTtl().toSeconds(),
				user.getId());
	}

	private static Set<String> roleNames(UserEntity user) {
		return user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
	}

	private static UserResponse toUserResponse(UserEntity user) {
		return new UserResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail(),
				roleNames(user),
				user.getCreatedAt());
	}
}

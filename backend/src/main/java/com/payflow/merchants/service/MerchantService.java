package com.payflow.merchants.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.identity.service.SecurityAuditService;
import com.payflow.merchants.api.dto.CreateMerchantRequest;
import com.payflow.merchants.api.dto.MerchantResponse;
import com.payflow.merchants.api.dto.UpdateMerchantRequest;
import com.payflow.merchants.domain.MerchantEntity;
import com.payflow.merchants.domain.MerchantMembershipEntity;
import com.payflow.merchants.domain.MerchantMembershipRole;
import com.payflow.merchants.domain.MerchantStatus;
import com.payflow.merchants.persistence.MerchantMembershipRepository;
import com.payflow.merchants.persistence.MerchantRepository;
import com.payflow.shared.error.ApiException;

@Service
public class MerchantService {

	private final MerchantRepository merchantRepository;
	private final MerchantMembershipRepository membershipRepository;
	private final SecurityAuditService securityAuditService;

	public MerchantService(MerchantRepository merchantRepository,
			MerchantMembershipRepository membershipRepository,
			SecurityAuditService securityAuditService) {
		this.merchantRepository = merchantRepository;
		this.membershipRepository = membershipRepository;
		this.securityAuditService = securityAuditService;
	}

	@Transactional
	public MerchantResponse create(UUID userId, CreateMerchantRequest request) {
		Instant now = Instant.now();
		UUID merchantId = UUID.randomUUID();
		MerchantEntity merchant = new MerchantEntity(
				merchantId,
				request.name().trim(),
				normalizeDescription(request.description()),
				MerchantStatus.ACTIVE,
				userId,
				now);
		merchantRepository.save(merchant);
		membershipRepository.save(new MerchantMembershipEntity(
				UUID.randomUUID(), merchantId, userId, MerchantMembershipRole.OWNER, now));
		securityAuditService.record("MERCHANT_CREATED", userId, merchantId, null, "merchant created", null);
		return toResponse(merchant);
	}

	@Transactional(readOnly = true)
	public List<MerchantResponse> listForUser(UUID userId) {
		return merchantRepository.findAllAccessible(userId).stream().map(MerchantService::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public MerchantResponse getForUser(UUID userId, UUID merchantId) {
		return toResponse(requireAccessible(userId, merchantId));
	}

	@Transactional
	public MerchantResponse updateForUser(UUID userId, UUID merchantId, UpdateMerchantRequest request) {
		requireOwner(userId, merchantId);
		MerchantEntity merchant = requireAccessible(userId, merchantId);
		if (request.name() != null) {
			merchant.setName(request.name().trim());
		}
		if (request.description() != null) {
			merchant.setDescription(normalizeDescription(request.description()));
		}
		if (request.status() != null) {
			merchant.setStatus(request.status());
		}
		merchant.touch(Instant.now());
		return toResponse(merchant);
	}

	@Transactional(readOnly = true)
	public MerchantEntity requireAccessible(UUID userId, UUID merchantId) {
		return merchantRepository.findAccessible(merchantId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Merchant not found"));
	}

	@Transactional(readOnly = true)
	public void requireOwner(UUID userId, UUID merchantId) {
		boolean owner = membershipRepository.existsByMerchantIdAndUserIdAndRole(
				merchantId, userId, MerchantMembershipRole.OWNER);
		if (!owner) {
			// Hide existence from unauthorized users
			throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Merchant not found");
		}
	}

	private static String normalizeDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.trim();
	}

	private static MerchantResponse toResponse(MerchantEntity merchant) {
		return new MerchantResponse(
				merchant.getId(),
				merchant.getName(),
				merchant.getDescription(),
				merchant.getStatus(),
				merchant.getOwnerUserId(),
				merchant.getCreatedAt(),
				merchant.getUpdatedAt());
	}
}

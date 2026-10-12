package com.payflow.merchants.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.merchants.api.dto.CreateMerchantRequest;
import com.payflow.merchants.api.dto.MerchantResponse;
import com.payflow.merchants.api.dto.UpdateMerchantRequest;
import com.payflow.merchants.service.MerchantService;
import com.payflow.shared.security.SecuritySupport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchants")
@SecurityRequirement(name = "bearer-jwt")
public class MerchantController {

	private final MerchantService merchantService;
	private final SecuritySupport securitySupport;

	public MerchantController(MerchantService merchantService, SecuritySupport securitySupport) {
		this.merchantService = merchantService;
		this.securitySupport = securitySupport;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a merchant account")
	public MerchantResponse create(@Valid @RequestBody CreateMerchantRequest request) {
		return merchantService.create(securitySupport.requireUserId(), request);
	}

	@GetMapping
	@Operation(summary = "List merchants accessible to the current user")
	public List<MerchantResponse> list() {
		return merchantService.listForUser(securitySupport.requireUserId());
	}

	@GetMapping("/{merchantId}")
	@Operation(summary = "Get a merchant by id")
	public MerchantResponse get(@PathVariable UUID merchantId) {
		return merchantService.getForUser(securitySupport.requireUserId(), merchantId);
	}

	@PatchMapping("/{merchantId}")
	@Operation(summary = "Update a merchant owned by the current user")
	public MerchantResponse update(@PathVariable UUID merchantId, @Valid @RequestBody UpdateMerchantRequest request) {
		return merchantService.updateForUser(securitySupport.requireUserId(), merchantId, request);
	}
}

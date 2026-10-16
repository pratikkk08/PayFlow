package com.payflow.analytics.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.analytics.api.dto.AnalyticsDailyResponse;
import com.payflow.analytics.api.dto.AnalyticsOverviewResponse;
import com.payflow.analytics.service.MerchantAnalyticsService;
import com.payflow.shared.security.SecuritySupport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/analytics")
@Tag(name = "Merchant analytics")
@SecurityRequirement(name = "bearer-jwt")
public class MerchantAnalyticsController {

	private final MerchantAnalyticsService analyticsService;
	private final SecuritySupport securitySupport;

	public MerchantAnalyticsController(
			MerchantAnalyticsService analyticsService, SecuritySupport securitySupport) {
		this.analyticsService = analyticsService;
		this.securitySupport = securitySupport;
	}

	@GetMapping("/overview")
	@Operation(summary = "Merchant analytics overview (JWT OWNER only; UTC reporting window)")
	public AnalyticsOverviewResponse overview(
			@PathVariable UUID merchantId,
			@Parameter(description = "Inclusive UTC start date (yyyy-MM-dd). Default: to − 29 days.")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "Inclusive UTC end date (yyyy-MM-dd). Default: today UTC.")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return analyticsService.overview(securitySupport.requireUserId(), merchantId, from, to);
	}

	@GetMapping("/daily")
	@Operation(summary = "Merchant daily analytics series (JWT OWNER only; UTC reporting window)")
	public AnalyticsDailyResponse daily(
			@PathVariable UUID merchantId,
			@Parameter(description = "Inclusive UTC start date (yyyy-MM-dd). Default: to − 29 days.")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "Inclusive UTC end date (yyyy-MM-dd). Default: today UTC.")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return analyticsService.daily(securitySupport.requireUserId(), merchantId, from, to);
	}
}

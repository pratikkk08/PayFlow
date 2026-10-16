package com.payflow.operations.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.operations.EventOperationsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/internal/events")
@Tag(name = "Internal operations")
public class EventOperationsController {

	private final EventOperationsService operationsService;

	public EventOperationsController(EventOperationsService operationsService) {
		this.operationsService = operationsService;
	}

	@GetMapping("/health")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Aggregate outbox and webhook operational counts",
			security = @SecurityRequirement(name = "bearer-jwt"))
	public EventOperationsHealthResponse health() {
		return EventOperationsHealthResponse.from(operationsService.refresh());
	}
}

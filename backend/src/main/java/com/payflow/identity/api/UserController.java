package com.payflow.identity.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.identity.api.dto.UserResponse;
import com.payflow.identity.service.AuthService;
import com.payflow.shared.security.SecuritySupport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

	private final AuthService authService;
	private final SecuritySupport securitySupport;

	public UserController(AuthService authService, SecuritySupport securitySupport) {
		this.authService = authService;
		this.securitySupport = securitySupport;
	}

	@GetMapping("/me")
	@Operation(summary = "Current authenticated user profile", security = @SecurityRequirement(name = "bearer-jwt"))
	public UserResponse me() {
		return authService.getMe(securitySupport.requireUserId());
	}
}

package com.payflow.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.identity.api.dto.AuthTokensResponse;
import com.payflow.identity.api.dto.LoginRequest;
import com.payflow.identity.api.dto.LogoutRequest;
import com.payflow.identity.api.dto.RefreshRequest;
import com.payflow.identity.api.dto.RegisterRequest;
import com.payflow.identity.api.dto.UserResponse;
import com.payflow.identity.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Register a new user")
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	@Operation(summary = "Login and obtain access + refresh tokens")
	public AuthTokensResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate refresh token and issue a new access token")
	public AuthTokensResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoke refresh token family")
	public void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
	}
}

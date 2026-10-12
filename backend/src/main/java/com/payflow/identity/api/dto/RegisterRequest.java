package com.payflow.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Size(min = 2, max = 200) String fullName,
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank
		@Size(min = 10, max = 128)
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
				message = "Password must contain upper, lower, and digit characters")
		String password) {
}

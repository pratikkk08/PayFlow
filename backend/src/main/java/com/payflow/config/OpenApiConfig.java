package com.payflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI payflowOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("PayFlow API")
						.version("v1")
						.description("Simulated payment platform — Phase 2 identity & merchants"))
				.components(new Components()
						.addSecuritySchemes("bearer-jwt", new SecurityScheme()
								.name("bearer-jwt")
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("User access JWT from /api/v1/auth/login"))
						.addSecuritySchemes("bearer-api-key", new SecurityScheme()
								.name("bearer-api-key")
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("sk_test_...")
								.description("Merchant secret API key (sk_test_...)")));
	}
}

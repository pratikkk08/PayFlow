package com.payflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.payflow.shared.security.PayflowSecurityProperties;
import com.payflow.shared.security.ProductionSecurityValidator;

/**
 * Runs fail-fast production security checks as soon as the application context is ready.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSecurityConfig implements ApplicationRunner {

	private final Environment environment;
	private final PayflowSecurityProperties securityProperties;
	private final String datasourceUrl;
	private final String datasourceUsername;
	private final String datasourcePassword;

	public ProductionSecurityConfig(
			Environment environment,
			PayflowSecurityProperties securityProperties,
			@Value("${spring.datasource.url:}") String datasourceUrl,
			@Value("${spring.datasource.username:}") String datasourceUsername,
			@Value("${spring.datasource.password:}") String datasourcePassword) {
		this.environment = environment;
		this.securityProperties = securityProperties;
		this.datasourceUrl = datasourceUrl;
		this.datasourceUsername = datasourceUsername;
		this.datasourcePassword = datasourcePassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!environment.acceptsProfiles(Profiles.of("prod"))) {
			return;
		}
		ProductionSecurityValidator.validate(
				securityProperties, datasourceUrl, datasourceUsername, datasourcePassword);
	}
}

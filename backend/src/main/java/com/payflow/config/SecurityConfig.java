package com.payflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import com.payflow.shared.security.ApiKeyAuthenticationFilter;
import com.payflow.shared.security.AuthRateLimitFilter;
import com.payflow.shared.security.PayflowBearerTokenResolver;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
	private final AuthRateLimitFilter authRateLimitFilter;
	private final PayflowBearerTokenResolver bearerTokenResolver;
	private final JwtAuthenticationConverter jwtAuthenticationConverter;

	public SecurityConfig(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
			AuthRateLimitFilter authRateLimitFilter,
			PayflowBearerTokenResolver bearerTokenResolver,
			JwtAuthenticationConverter jwtAuthenticationConverter) {
		this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
		this.authRateLimitFilter = authRateLimitFilter;
		this.bearerTokenResolver = bearerTokenResolver;
		this.jwtAuthenticationConverter = jwtAuthenticationConverter;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.anonymous(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/v1/system/ping").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/internal/**").hasRole("ADMIN")
						.requestMatchers(
								"/api/v1/auth/register",
								"/api/v1/auth/login",
								"/api/v1/auth/refresh",
								"/api/v1/auth/logout")
						.permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.bearerTokenResolver(bearerTokenResolver)
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.addFilterBefore(authRateLimitFilter, BearerTokenAuthenticationFilter.class)
				.addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}

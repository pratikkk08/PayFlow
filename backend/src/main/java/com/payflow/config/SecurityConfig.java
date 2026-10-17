package com.payflow.config;

import java.util.List;
import java.util.function.Supplier;

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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.payflow.shared.security.ApiKeyAuthenticationFilter;
import com.payflow.shared.security.AuthRateLimitFilter;
import com.payflow.shared.security.PayflowBearerTokenResolver;
import com.payflow.shared.security.PayflowSecurityProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
	private final AuthRateLimitFilter authRateLimitFilter;
	private final PayflowBearerTokenResolver bearerTokenResolver;
	private final JwtAuthenticationConverter jwtAuthenticationConverter;
	private final PayflowSecurityProperties securityProperties;

	public SecurityConfig(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
			AuthRateLimitFilter authRateLimitFilter,
			PayflowBearerTokenResolver bearerTokenResolver,
			JwtAuthenticationConverter jwtAuthenticationConverter,
			PayflowSecurityProperties securityProperties) {
		this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
		this.authRateLimitFilter = authRateLimitFilter;
		this.bearerTokenResolver = bearerTokenResolver;
		this.jwtAuthenticationConverter = jwtAuthenticationConverter;
		this.securityProperties = securityProperties;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		boolean cookieSecure = securityProperties.getBrowserSession().isCookieSecure();
		CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfTokenRepository.setCookiePath("/");
		csrfTokenRepository.setCookieCustomizer(cookie -> cookie
				.sameSite(securityProperties.getBrowserSession().getCookieSameSite())
				.secure(cookieSecure));

		RequestMatcher browserSessionCsrf = new OrRequestMatcher(
				PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/browser/login"),
				PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/browser/refresh"),
				PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/browser/logout"));

		http
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
						.requireCsrfProtectionMatcher(browserSessionCsrf))
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
								"/api/v1/auth/logout",
								"/api/v1/auth/browser/**")
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
				.addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class)
				.addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class);

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		List<String> origins = securityProperties.getCors().getAllowedOrigins();
		configuration.setAllowedOrigins(origins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "X-XSRF-TOKEN"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	/**
	 * SPA-friendly CSRF: BREACH-resistant XOR for form params; plain token accepted from header.
	 * Forces token materialization so the non-HttpOnly XSRF-TOKEN cookie is written.
	 */
	static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

		private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
		private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response,
				Supplier<CsrfToken> csrfToken) {
			this.xor.handle(request, response, csrfToken);
			csrfToken.get();
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
			String headerValue = request.getHeader(csrfToken.getHeaderName());
			return (StringUtils.hasText(headerValue) ? this.plain : this.xor)
					.resolveCsrfTokenValue(request, csrfToken);
		}
	}

	/**
	 * Ensures deferred CSRF tokens are loaded so the cookie is present for the SPA.
	 */
	static final class CsrfCookieFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, java.io.IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			if (csrfToken != null) {
				csrfToken.getToken();
			}
			filterChain.doFilter(request, response);
		}
	}
}

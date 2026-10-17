package com.payflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.payflow.identity.service.RefreshCookieService;
import com.payflow.shared.security.PayflowSecurityProperties;

class RefreshCookieServiceTest {

	@Test
	void productionCookieIsHttpOnlySecureWithBrowserPath() {
		PayflowSecurityProperties props = new PayflowSecurityProperties();
		props.getBrowserSession().setCookieSecure(true);
		props.getBrowserSession().setCookieSameSite("Lax");
		props.getBrowserSession().setRefreshCookieName("payflow_refresh");
		props.getBrowserSession().setRefreshCookiePath("/api/v1/auth/browser");
		props.getRefreshToken().setTtl(Duration.ofDays(7));

		RefreshCookieService service = new RefreshCookieService(props);
		MockHttpServletResponse response = new MockHttpServletResponse();
		service.writeRefreshCookie(response, "opaque-refresh-token-value");

		String setCookie = response.getHeader("Set-Cookie");
		assertThat(setCookie).isNotNull();
		assertThat(setCookie).contains("payflow_refresh=");
		assertThat(setCookie).contains("HttpOnly");
		assertThat(setCookie).contains("Secure");
		assertThat(setCookie).contains("Path=/api/v1/auth/browser");
		assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
	}

	@Test
	void localCookieOmitsSecureFlag() {
		PayflowSecurityProperties props = new PayflowSecurityProperties();
		props.getBrowserSession().setCookieSecure(false);
		props.getBrowserSession().setCookieSameSite("Lax");
		props.getRefreshToken().setTtl(Duration.ofDays(7));

		RefreshCookieService service = new RefreshCookieService(props);
		MockHttpServletResponse response = new MockHttpServletResponse();
		service.writeRefreshCookie(response, "opaque-refresh-token-value");

		String setCookie = response.getHeader("Set-Cookie");
		assertThat(setCookie).contains("HttpOnly");
		assertThat(setCookie).doesNotContain("Secure");
	}
}

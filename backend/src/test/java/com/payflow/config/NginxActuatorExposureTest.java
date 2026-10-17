package com.payflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guards the frontend Nginx template: only exact health probe paths are proxied;
 * other Actuator routes must 404. Does not start Nginx.
 */
class NginxActuatorExposureTest {

	@Test
	void nginxProxiesOnlyExactHealthProbePaths() throws Exception {
		Path template = Path.of("..", "frontend", "nginx", "default.conf.template").normalize();
		if (!Files.isRegularFile(template)) {
			template = Path.of("frontend", "nginx", "default.conf.template");
		}
		assertThat(template).exists();
		String conf = Files.readString(template);

		assertThat(conf).contains("location = /actuator/health {");
		assertThat(conf).contains("location = /actuator/health/liveness {");
		assertThat(conf).contains("location = /actuator/health/readiness {");
		assertThat(conf).contains("location /actuator/ {");
		assertThat(conf).contains("return 404;");

		assertThat(conf).doesNotContain("proxy_pass http://payflow_backend/actuator/metrics");
		assertThat(conf).doesNotContain("location /actuator/health/");
	}
}

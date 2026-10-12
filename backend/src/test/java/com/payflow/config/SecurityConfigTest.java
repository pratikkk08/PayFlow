package com.payflow.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.payflow.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void pingIsPublic() throws Exception {
		mockMvc.perform(get("/api/v1/system/ping"))
				.andExpect(status().isOk());
	}

	@Test
	void unknownApiRouteIsNotPubliclyAccessible() throws Exception {
		mockMvc.perform(get("/api/v1/merchants"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void actuatorHealthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}
}

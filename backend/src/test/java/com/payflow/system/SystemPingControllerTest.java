package com.payflow.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.payflow.config.SecurityConfig;

@WebMvcTest(controllers = SystemPingController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SystemPingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void pingReturnsExpectedPayload() throws Exception {
		mockMvc.perform(get("/api/v1/system/ping"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.service").value("payflow-backend"));
	}

}

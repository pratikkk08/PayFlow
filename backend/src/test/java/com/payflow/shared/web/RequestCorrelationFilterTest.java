package com.payflow.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.payflow.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RequestCorrelationFilterTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void generatesRequestIdWhenAbsent() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/system/ping"))
				.andExpect(status().isOk())
				.andExpect(header().exists(RequestCorrelationFilter.HEADER))
				.andReturn();
		String id = result.getResponse().getHeader(RequestCorrelationFilter.HEADER);
		assertThat(id).isNotBlank().hasSizeLessThanOrEqualTo(64);
	}

	@Test
	void propagatesSafeIncomingRequestId() throws Exception {
		mockMvc.perform(get("/api/v1/system/ping")
						.header(RequestCorrelationFilter.HEADER, "e2e-corr-abc_123"))
				.andExpect(status().isOk())
				.andExpect(header().string(RequestCorrelationFilter.HEADER, "e2e-corr-abc_123"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			" ",
			"bad id with spaces",
			"has/slash",
			"Authorization:Bearer.xyz",
			"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	})
	void rejectsUnsafeIncomingIds(String unsafe) throws Exception {
		assertThat(RequestCorrelationFilter.sanitize(unsafe)).isNull();
	}

	@Test
	void healthProbesArePublicWithoutDetails() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());

		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));

		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}
}

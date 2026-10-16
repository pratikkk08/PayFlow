package com.payflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PayflowBackendApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoadsAndDatabaseIsReachable() {
		Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
		assertThat(one).isEqualTo(1);
	}

	@Test
	void flywayBaselineMigrationApplied() {
		String phase = jdbcTemplate.queryForObject(
				"SELECT schema_phase FROM payflow_schema_meta WHERE id = 1",
				String.class);
		assertThat(phase).isEqualTo("phase-6");
	}

}

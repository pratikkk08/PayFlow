package com.payflow.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.payflow.TestcontainersConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayCreatesBaselineMetaTable() {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payflow_schema_meta",
				Integer.class);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void flywaySchemaHistoryContainsV1() {
		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

}

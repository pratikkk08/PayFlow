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
	void flywayAppliesPhase2Migrations() {
		Integer users = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'users'",
				Integer.class);
		assertThat(users).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1','2') AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(2);
	}

	@Test
	void flywayAppliesPhase3PaymentsMigration() {
		String phase = jdbcTemplate.queryForObject(
				"SELECT schema_phase FROM payflow_schema_meta WHERE id = 1",
				String.class);
		assertThat(phase).isEqualTo("phase-3");

		Integer payments = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'payments'",
				Integer.class);
		assertThat(payments).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesPaymentIdempotencyMigration() {
		Integer records = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'payment_idempotency_records'",
				Integer.class);
		assertThat(records).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesPaymentAuthorizationMigration() {
		Integer failureCode = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.columns
						WHERE table_name = 'payments' AND column_name = 'failure_code'
						""",
				Integer.class);
		assertThat(failureCode).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesPaymentCaptureCancelMigration() {
		Integer capturedAt = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.columns
						WHERE table_name = 'payments' AND column_name = 'captured_at'
						""",
				Integer.class);
		assertThat(capturedAt).isEqualTo(1);

		Integer cancelledAt = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.columns
						WHERE table_name = 'payments' AND column_name = 'cancelled_at'
						""",
				Integer.class);
		assertThat(cancelledAt).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesPaymentListIndexesMigration() {
		Integer index = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM pg_indexes
						WHERE tablename = 'payments' AND indexname = 'idx_payments_merchant_created_id'
						""",
				Integer.class);
		assertThat(index).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}
}



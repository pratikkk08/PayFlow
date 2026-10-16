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

	@Test
	void flywayAppliesRefundsMigration() {
		Integer refunds = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'refunds'",
				Integer.class);
		assertThat(refunds).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesRefundIdempotencyMigration() {
		Integer records = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'refund_idempotency_records'",
				Integer.class);
		assertThat(records).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '9' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesLedgerFoundationMigration() {
		Integer accounts = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ledger_accounts'",
				Integer.class);
		assertThat(accounts).isEqualTo(1);

		Integer transactions = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ledger_transactions'",
				Integer.class);
		assertThat(transactions).isEqualTo(1);

		Integer entries = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ledger_entries'",
				Integer.class);
		assertThat(entries).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '10' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesRefundListIndexMigration() {
		Integer index = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM pg_indexes
						WHERE tablename = 'refunds' AND indexname = 'idx_refunds_payment_created_id'
						""",
				Integer.class);
		assertThat(index).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '11' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesTransactionalOutboxMigration() {
		String phase = jdbcTemplate.queryForObject(
				"SELECT schema_phase FROM payflow_schema_meta WHERE id = 1",
				String.class);
		assertThat(phase).isEqualTo("phase-5");

		Integer outbox = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'outbox_events'",
				Integer.class);
		assertThat(outbox).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '12' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesOutboxClaimTokenMigration() {
		Integer column = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.columns
						WHERE table_name = 'outbox_events' AND column_name = 'claim_token'
						""",
				Integer.class);
		assertThat(column).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '13' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesProcessedEventsMigration() {
		Integer table = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.tables
						WHERE table_name = 'processed_events'
						""",
				Integer.class);
		assertThat(table).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '14' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesWebhookEndpointsMigration() {
		Integer table = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.tables
						WHERE table_name = 'webhook_endpoints'
						""",
				Integer.class);
		assertThat(table).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '15' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void flywayAppliesWebhookDeliveriesMigration() {
		Integer table = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.tables
						WHERE table_name = 'webhook_deliveries'
						""",
				Integer.class);
		assertThat(table).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '16' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}
}



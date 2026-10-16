package com.payflow.refunds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.payflow.TestcontainersConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefundSchemaTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void refundsTableExistsWithExpectedConstraints() {
		Integer tables = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'refunds'",
				Integer.class);
		assertThat(tables).isEqualTo(1);

		Integer amountCheck = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.check_constraints
						WHERE constraint_name = 'ck_refunds_amount_positive'
						""",
				Integer.class);
		assertThat(amountCheck).isEqualTo(1);

		Integer statusCheck = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.check_constraints
						WHERE constraint_name = 'ck_refunds_status'
						""",
				Integer.class);
		assertThat(statusCheck).isEqualTo(1);

		Integer completedCheck = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.check_constraints
						WHERE constraint_name = 'ck_refunds_completed_at'
						""",
				Integer.class);
		assertThat(completedCheck).isEqualTo(1);

		Integer uniqueRef = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.table_constraints
						WHERE table_name = 'refunds' AND constraint_name = 'uq_refunds_merchant_reference'
						  AND constraint_type = 'UNIQUE'
						""",
				Integer.class);
		assertThat(uniqueRef).isEqualTo(1);
	}

	@Test
	void acceptsValidPendingAndSucceededRefundsAndRejectsInvalidRows() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		UUID pendingRefundId = UUID.randomUUID();
		UUID succeededRefundId = UUID.randomUUID();
		String email = "schema-refund-" + userId + "@example.com";

		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Refund Schema Tester', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Refund Schema Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);
		jdbcTemplate.update(
				"""
						INSERT INTO payments (id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, 'pay-refund-base', 5000, 'INR', 'CAPTURED')
						""",
				paymentId, merchantId);

		jdbcTemplate.update(
				"""
						INSERT INTO refunds (id, payment_id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, ?, 'ref-partial-1', 1500, 'INR', 'PENDING')
						""",
				pendingRefundId, paymentId, merchantId);

		jdbcTemplate.update(
				"""
						INSERT INTO refunds (id, payment_id, merchant_id, merchant_reference, amount_minor, currency, status,
						                     completed_at)
						VALUES (?, ?, ?, 'ref-partial-2', 2000, 'INR', 'SUCCEEDED', NOW())
						""",
				succeededRefundId, paymentId, merchantId);

		Long reservedOrSucceeded = jdbcTemplate.queryForObject(
				"""
						SELECT COALESCE(SUM(amount_minor), 0) FROM refunds
						WHERE payment_id = ? AND status IN ('PENDING', 'SUCCEEDED')
						""",
				Long.class,
				paymentId);
		assertThat(reservedOrSucceeded).isEqualTo(3500L);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO refunds (id, payment_id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, ?, 'ref-bad-amount', 0, 'INR', 'PENDING')
						""",
				UUID.randomUUID(), paymentId, merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO refunds (id, payment_id, merchant_id, merchant_reference, amount_minor, currency, status,
						                     completed_at)
						VALUES (?, ?, ?, 'ref-bad-pending-completed', 100, 'INR', 'PENDING', NOW())
						""",
				UUID.randomUUID(), paymentId, merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO refunds (id, payment_id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, ?, 'ref-bad-succeeded-open', 100, 'INR', 'SUCCEEDED')
						""",
				UUID.randomUUID(), paymentId, merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO refunds (id, payment_id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, ?, 'ref-partial-1', 100, 'INR', 'PENDING')
						""",
				UUID.randomUUID(), paymentId, merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}

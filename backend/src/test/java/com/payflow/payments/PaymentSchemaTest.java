package com.payflow.payments;

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
class PaymentSchemaTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void paymentsTableExistsWithExpectedConstraints() {
		Integer tables = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'payments'",
				Integer.class);
		assertThat(tables).isEqualTo(1);

		Integer amountCheck = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.check_constraints
						WHERE constraint_name = 'ck_payments_amount_positive'
						""",
				Integer.class);
		assertThat(amountCheck).isEqualTo(1);

		Integer statusCheck = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.check_constraints
						WHERE constraint_name = 'ck_payments_status'
						""",
				Integer.class);
		assertThat(statusCheck).isEqualTo(1);
	}

	@Test
	void acceptsValidPaymentRowAndRejectsNonPositiveAmount() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String email = "schema-pay-" + userId + "@example.com";

		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Schema Tester', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Schema Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);
		jdbcTemplate.update(
				"""
						INSERT INTO payments (id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, 'ord-1001', 2599, 'INR', 'CREATED')
						""",
				paymentId, merchantId);

		Long amount = jdbcTemplate.queryForObject(
				"SELECT amount_minor FROM payments WHERE id = ?",
				Long.class,
				paymentId);
		assertThat(amount).isEqualTo(2599L);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO payments (id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, 'ord-bad', 0, 'INR', 'CREATED')
						""",
				UUID.randomUUID(), merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}

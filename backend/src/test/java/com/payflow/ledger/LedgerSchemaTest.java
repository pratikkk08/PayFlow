package com.payflow.ledger;

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
class LedgerSchemaTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void ledgerTablesExistWithExpectedConstraints() {
		assertThat(tableCount("ledger_accounts")).isEqualTo(1);
		assertThat(tableCount("ledger_transactions")).isEqualTo(1);
		assertThat(tableCount("ledger_entries")).isEqualTo(1);

		assertThat(checkConstraintCount("ck_ledger_accounts_type")).isEqualTo(1);
		assertThat(checkConstraintCount("ck_ledger_transactions_source_type")).isEqualTo(1);
		assertThat(checkConstraintCount("ck_ledger_entries_direction")).isEqualTo(1);
		assertThat(checkConstraintCount("ck_ledger_entries_amount_positive")).isEqualTo(1);

		Integer uniqueSource = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.table_constraints
						WHERE table_name = 'ledger_transactions'
						  AND constraint_name = 'uq_ledger_transactions_source'
						  AND constraint_type = 'UNIQUE'
						""",
				Integer.class);
		assertThat(uniqueSource).isEqualTo(1);
	}

	@Test
	void acceptsBalancedCaptureShapeAndRejectsInvalidRows() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		UUID clearingId = UUID.randomUUID();
		UUID payableId = UUID.randomUUID();
		UUID txnId = UUID.randomUUID();
		UUID debitId = UUID.randomUUID();
		UUID creditId = UUID.randomUUID();
		UUID sourceId = UUID.randomUUID();
		String email = "ledger-schema-" + userId + "@example.com";

		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Ledger Schema', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Ledger Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);

		jdbcTemplate.update(
				"""
						INSERT INTO ledger_accounts (id, merchant_id, account_type, currency, active)
						VALUES (?, NULL, 'PROCESSOR_CLEARING', 'INR', TRUE)
						""",
				clearingId);
		jdbcTemplate.update(
				"""
						INSERT INTO ledger_accounts (id, merchant_id, account_type, currency, active)
						VALUES (?, ?, 'MERCHANT_PAYABLE', 'INR', TRUE)
						""",
				payableId, merchantId);

		jdbcTemplate.update(
				"""
						INSERT INTO ledger_transactions (id, source_type, source_id, merchant_id, currency)
						VALUES (?, 'PAYMENT_CAPTURE', ?, ?, 'INR')
						""",
				txnId, sourceId, merchantId);

		jdbcTemplate.update(
				"""
						INSERT INTO ledger_entries (id, ledger_transaction_id, ledger_account_id, direction, amount_minor)
						VALUES (?, ?, ?, 'DEBIT', 2500)
						""",
				debitId, txnId, clearingId);
		jdbcTemplate.update(
				"""
						INSERT INTO ledger_entries (id, ledger_transaction_id, ledger_account_id, direction, amount_minor)
						VALUES (?, ?, ?, 'CREDIT', 2500)
						""",
				creditId, txnId, payableId);

		Long debits = jdbcTemplate.queryForObject(
				"""
						SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries
						WHERE ledger_transaction_id = ? AND direction = 'DEBIT'
						""",
				Long.class, txnId);
		Long credits = jdbcTemplate.queryForObject(
				"""
						SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries
						WHERE ledger_transaction_id = ? AND direction = 'CREDIT'
						""",
				Long.class, txnId);
		assertThat(debits).isEqualTo(credits).isEqualTo(2500L);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO ledger_transactions (id, source_type, source_id, merchant_id, currency)
						VALUES (?, 'PAYMENT_CAPTURE', ?, ?, 'INR')
						""",
				UUID.randomUUID(), sourceId, merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO ledger_entries (id, ledger_transaction_id, ledger_account_id, direction, amount_minor)
						VALUES (?, ?, ?, 'DEBIT', 0)
						""",
				UUID.randomUUID(), txnId, clearingId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO ledger_accounts (id, merchant_id, account_type, currency, active)
						VALUES (?, ?, 'PROCESSOR_CLEARING', 'INR', TRUE)
						""",
				UUID.randomUUID(), merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO ledger_accounts (id, merchant_id, account_type, currency, active)
						VALUES (?, NULL, 'MERCHANT_PAYABLE', 'INR', TRUE)
						""",
				UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Integer tableCount(String tableName) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
				Integer.class,
				tableName);
	}

	private Integer checkConstraintCount(String constraintName) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.check_constraints
						WHERE constraint_name = ?
						""",
				Integer.class,
				constraintName);
	}
}

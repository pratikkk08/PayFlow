package com.payflow.risk.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.payments.domain.PaymentEntity;
import com.payflow.payments.persistence.PaymentRepository;
import com.payflow.risk.domain.RiskDecision;
import com.payflow.risk.domain.RiskRuleAction;
import com.payflow.risk.domain.RiskRuleEntity;
import com.payflow.risk.persistence.RiskAuthorizationAttemptRepository;
import com.payflow.risk.persistence.RiskRuleRepository;
import com.payflow.risk.service.RiskRuleParameterParser.AmountThresholdParams;
import com.payflow.risk.service.RiskRuleParameterParser.InvalidRiskRuleParametersException;
import com.payflow.risk.service.RiskRuleParameterParser.RepeatedFailuresParams;
import com.payflow.risk.service.RiskRuleParameterParser.VelocityParams;
import com.payflow.shared.error.ApiException;

/**
 * Deterministic, merchant-isolated risk evaluation over PostgreSQL.
 *
 * <p>Standalone {@link #evaluate(UUID)} is read-only (no attempt insert). Authorization
 * calls {@link #evaluateForAuthorization(PaymentEntity)} after payment → merchant locks,
 * which records an idempotent authorize attempt then evaluates. Assessments are persisted
 * by {@link RiskAssessmentWriter}, not this service.
 *
 * <h2>Rule evaluation order</h2>
 * Enabled rules for the payment's merchant, ordered by {@code priority ASC}, then {@code id ASC}.
 *
 * <h2>Boundary semantics</h2>
 * <ul>
 *   <li>{@link RiskRuleType#AMOUNT_THRESHOLD}: matches when
 *       {@code payment.amountMinor > thresholdMinor} (strict greater-than).</li>
 *   <li>{@link RiskRuleType#PAYMENT_VELOCITY}: counts <b>peer authorize attempts</b>
 *       ({@code risk_authorization_attempts}) with {@code attemptedAt} in
 *       {@code [evaluatedAt - windowSeconds, evaluatedAt]} inclusive, excluding the current
 *       payment. Matches when {@code count >= maxCount}. Attempts include ALLOW/FLAG/REVIEW/BLOCK
 *       outcomes; retries of the same payment do not insert a second row.</li>
 *   <li>{@link RiskRuleType#REPEATED_FAILURES}: counts other merchant payments with
 *       {@code status = FAILED} and {@code updatedAt} in the same inclusive window,
 *       excluding the current payment. Matches when {@code count >= maxFailures}.</li>
 * </ul>
 *
 * <h2>Concurrency (Task 6.4)</h2>
 * Authorize serializes per merchant (payment lock, then merchant lock) before recording an
 * attempt and evaluating, so peer attempts/failures committed by prior authorizes are visible.
 * Lock order with cancel/capture (payment-only) cannot deadlock against authorize (payment→merchant).
 *
 * <h2>Clock / window limitations</h2>
 * Windows use the injected {@link java.time.Clock} ({@code attempted_at} / evaluation instant and,
 * on authorize, payment {@code updated_at} for FAILED). Wall-clock skew across app instances is not
 * corrected; inclusive endpoints mean an event exactly at {@code windowStart} counts. Create-time
 * is not used for velocity (authorize attempts are).
 *
 * <h2>Malformed parameters (fail-closed)</h2>
 * Invalid BLOCK/REVIEW params → forced match ({@code RULE_CONFIG_INVALID}); invalid FLAG skipped.
 *
 * <h2>Scoring</h2>
 * See {@link RiskScoring}. Postgres is SoT; Redis is not used.
 */
@Service
public class RiskEvaluationService {

	public static final String REASON_AMOUNT_ABOVE_THRESHOLD = "AMOUNT_ABOVE_THRESHOLD";
	public static final String REASON_PAYMENT_VELOCITY_EXCEEDED = "PAYMENT_VELOCITY_EXCEEDED";
	public static final String REASON_REPEATED_FAILURES_EXCEEDED = "REPEATED_FAILURES_EXCEEDED";
	public static final String REASON_RULE_CONFIG_INVALID = "RULE_CONFIG_INVALID";

	private final PaymentRepository paymentRepository;
	private final RiskRuleRepository riskRuleRepository;
	private final RiskAuthorizationAttemptRepository attemptRepository;
	private final RiskAuthorizationAttemptRecorder attemptRecorder;
	private final RiskRuleParameterParser parameterParser;
	private final Clock clock;

	public RiskEvaluationService(
			PaymentRepository paymentRepository,
			RiskRuleRepository riskRuleRepository,
			RiskAuthorizationAttemptRepository attemptRepository,
			RiskAuthorizationAttemptRecorder attemptRecorder,
			RiskRuleParameterParser parameterParser,
			Clock clock) {
		this.paymentRepository = paymentRepository;
		this.riskRuleRepository = riskRuleRepository;
		this.attemptRepository = attemptRepository;
		this.attemptRecorder = attemptRecorder;
		this.parameterParser = parameterParser;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public RiskEvaluationResult evaluate(UUID paymentId) {
		PaymentEntity payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "payment_not_found",
						"Payment not found"));
		return evaluatePayment(payment);
	}

	/**
	 * Authorize-path evaluation: record idempotent attempt, then score under caller's TX
	 * (caller must hold payment then merchant locks).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public RiskEvaluationResult evaluateForAuthorization(PaymentEntity payment) {
		Instant evaluatedAt = clock.instant();
		attemptRecorder.recordAttempt(payment.getMerchantId(), payment.getId(), evaluatedAt);
		return evaluatePayment(payment, evaluatedAt);
	}

	private RiskEvaluationResult evaluatePayment(PaymentEntity payment) {
		return evaluatePayment(payment, clock.instant());
	}

	private RiskEvaluationResult evaluatePayment(PaymentEntity payment, Instant evaluatedAt) {
		UUID merchantId = payment.getMerchantId();

		List<RiskRuleEntity> rules = riskRuleRepository
				.findByMerchantIdAndEnabledTrueOrderByPriorityAscIdAsc(merchantId);

		String configurationVersion = configurationVersion(rules);
		List<RiskMatchedRule> matches = new ArrayList<>();

		for (RiskRuleEntity rule : rules) {
			if (!merchantId.equals(rule.getMerchantId())) {
				continue;
			}
			evaluateRule(payment, rule, evaluatedAt).ifPresent(matches::add);
		}

		RiskDecision decision = RiskDecision.ALLOW;
		int rawScore = 0;
		List<UUID> matchedRuleIds = new ArrayList<>(matches.size());
		List<String> reasonCodes = new ArrayList<>(matches.size());
		for (RiskMatchedRule match : matches) {
			decision = RiskScoring.maxSeverity(decision, RiskScoring.toDecision(match.matchedAction()));
			rawScore += match.scoreContribution();
			matchedRuleIds.add(match.ruleId());
			reasonCodes.add(match.reasonCode());
		}

		return new RiskEvaluationResult(
				payment.getId(),
				merchantId,
				RiskScoring.clamp(rawScore),
				decision,
				matchedRuleIds,
				reasonCodes,
				configurationVersion,
				evaluatedAt,
				matches);
	}

	private java.util.Optional<RiskMatchedRule> evaluateRule(
			PaymentEntity payment, RiskRuleEntity rule, Instant evaluatedAt) {
		try {
			return switch (rule.getRuleType()) {
				case AMOUNT_THRESHOLD -> evaluateAmountThreshold(payment, rule);
				case PAYMENT_VELOCITY -> evaluateVelocity(payment, rule, evaluatedAt);
				case REPEATED_FAILURES -> evaluateRepeatedFailures(payment, rule, evaluatedAt);
			};
		}
		catch (InvalidRiskRuleParametersException ex) {
			return failClosedIfNeeded(rule);
		}
	}

	private java.util.Optional<RiskMatchedRule> evaluateAmountThreshold(
			PaymentEntity payment, RiskRuleEntity rule) {
		AmountThresholdParams params = parameterParser.parseAmountThreshold(rule.getParameters());
		// Strict greater-than: equal to threshold does not match.
		if (payment.getAmountMinor() > params.thresholdMinor()) {
			return java.util.Optional.of(matched(rule, REASON_AMOUNT_ABOVE_THRESHOLD));
		}
		return java.util.Optional.empty();
	}

	private java.util.Optional<RiskMatchedRule> evaluateVelocity(
			PaymentEntity payment, RiskRuleEntity rule, Instant evaluatedAt) {
		VelocityParams params = parameterParser.parseVelocity(rule.getParameters());
		Instant windowStart = evaluatedAt.minusSeconds(params.windowSeconds());
		long count = attemptRepository.countAttemptsInWindowExcluding(
				payment.getMerchantId(), payment.getId(), windowStart, evaluatedAt);
		// Inclusive peer-attempt count vs maxCount: match when count >= maxCount.
		if (count >= params.maxCount()) {
			return java.util.Optional.of(matched(rule, REASON_PAYMENT_VELOCITY_EXCEEDED));
		}
		return java.util.Optional.empty();
	}

	private java.util.Optional<RiskMatchedRule> evaluateRepeatedFailures(
			PaymentEntity payment, RiskRuleEntity rule, Instant evaluatedAt) {
		RepeatedFailuresParams params = parameterParser.parseRepeatedFailures(rule.getParameters());
		Instant windowStart = evaluatedAt.minusSeconds(params.windowSeconds());
		long count = paymentRepository.countFailedUpdatedInWindowExcluding(
				payment.getMerchantId(), payment.getId(), windowStart, evaluatedAt);
		if (count >= params.maxFailures()) {
			return java.util.Optional.of(matched(rule, REASON_REPEATED_FAILURES_EXCEEDED));
		}
		return java.util.Optional.empty();
	}

	/**
	 * BLOCK/REVIEW config errors → forced match (fail closed). FLAG → skip (fail open).
	 */
	private java.util.Optional<RiskMatchedRule> failClosedIfNeeded(RiskRuleEntity rule) {
		RiskRuleAction action = rule.getAction();
		if (action == RiskRuleAction.BLOCK || action == RiskRuleAction.REVIEW) {
			return java.util.Optional.of(matched(rule, REASON_RULE_CONFIG_INVALID));
		}
		return java.util.Optional.empty();
	}

	private static RiskMatchedRule matched(RiskRuleEntity rule, String reasonCode) {
		return new RiskMatchedRule(
				rule.getId(),
				rule.getRuleType(),
				rule.getAction(),
				reasonCode,
				RiskScoring.contributionFor(rule.getAction()));
	}

	/**
	 * Opaque audit token derived from enabled rules (id + version + type + action + params).
	 * Later rule edits change this value; historical assessments keep their own snapshot id.
	 */
	static String configurationVersion(List<RiskRuleEntity> rules) {
		if (rules.isEmpty()) {
			return "cfg:empty";
		}
		StringBuilder fingerprint = new StringBuilder();
		for (RiskRuleEntity rule : rules) {
			fingerprint.append(rule.getId())
					.append('@')
					.append(rule.getVersion())
					.append(':')
					.append(rule.getRuleType())
					.append(':')
					.append(rule.getAction())
					.append(':')
					.append(rule.getParameters())
					.append('|');
		}
		return "cfg:" + sha256Hex(fingerprint.toString()).substring(0, 48);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}
}

package com.payflow.risk.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.risk.domain.RiskAssessmentEntity;
import com.payflow.risk.domain.RiskRuleMatchEntity;
import com.payflow.risk.persistence.RiskAssessmentRepository;
import com.payflow.risk.persistence.RiskRuleMatchRepository;
import com.payflow.shared.error.ApiException;

/**
 * Persists immutable risk assessment + matched-rule snapshots in the caller's transaction.
 * One assessment per payment (enforced by {@code uq_risk_assessments_payment}).
 */
@Service
public class RiskAssessmentWriter {

	private final RiskAssessmentRepository assessmentRepository;
	private final RiskRuleMatchRepository matchRepository;
	private final ObjectProvider<RiskAssessmentWriteFaultInjector> faultInjector;

	public RiskAssessmentWriter(
			RiskAssessmentRepository assessmentRepository,
			RiskRuleMatchRepository matchRepository,
			ObjectProvider<RiskAssessmentWriteFaultInjector> faultInjector) {
		this.assessmentRepository = assessmentRepository;
		this.matchRepository = matchRepository;
		this.faultInjector = faultInjector;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public RiskAssessmentEntity persist(RiskEvaluationResult evaluation) {
		RiskAssessmentWriteFaultInjector injector = faultInjector.getIfAvailable();
		if (injector != null) {
			injector.beforePersist();
		}

		Instant now = evaluation.evaluatedAt();
		UUID assessmentId = UUID.randomUUID();
		RiskAssessmentEntity assessment = new RiskAssessmentEntity(
				assessmentId,
				evaluation.paymentId(),
				evaluation.merchantId(),
				evaluation.decision(),
				evaluation.riskScore(),
				evaluation.evaluatedAt(),
				evaluation.reasonCodes().isEmpty() ? null : evaluation.reasonCodes(),
				evaluation.configurationVersion(),
				now);

		try {
			assessmentRepository.saveAndFlush(assessment);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "risk_assessment_exists",
					"A risk assessment already exists for this payment");
		}

		for (RiskMatchedRule match : evaluation.matches()) {
			matchRepository.save(new RiskRuleMatchEntity(
					UUID.randomUUID(),
					assessmentId,
					match.ruleId(),
					match.ruleType(),
					match.matchedAction(),
					match.reasonCode(),
					match.scoreContribution(),
					now));
		}
		matchRepository.flush();
		return assessment;
	}
}

package com.payflow.risk.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.risk.domain.RiskAssessmentEntity;
import com.payflow.risk.domain.RiskDecision;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessmentEntity, UUID> {

	Optional<RiskAssessmentEntity> findByPaymentId(UUID paymentId);

	List<RiskAssessmentEntity> findByMerchantIdOrderByEvaluatedAtDescIdDesc(UUID merchantId);

	List<RiskAssessmentEntity> findByMerchantIdAndDecisionOrderByEvaluatedAtDesc(UUID merchantId,
			RiskDecision decision);
}

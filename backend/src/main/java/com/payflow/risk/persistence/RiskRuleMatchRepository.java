package com.payflow.risk.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.risk.domain.RiskRuleMatchEntity;

public interface RiskRuleMatchRepository extends JpaRepository<RiskRuleMatchEntity, UUID> {

	List<RiskRuleMatchEntity> findByAssessmentIdOrderByCreatedAtAscIdAsc(UUID assessmentId);

	List<RiskRuleMatchEntity> findByRuleId(UUID ruleId);
}

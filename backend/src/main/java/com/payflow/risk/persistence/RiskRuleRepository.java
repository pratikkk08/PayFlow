package com.payflow.risk.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.risk.domain.RiskRuleEntity;
import com.payflow.risk.domain.RiskRuleType;

public interface RiskRuleRepository extends JpaRepository<RiskRuleEntity, UUID> {

	List<RiskRuleEntity> findByMerchantIdOrderByPriorityAscIdAsc(UUID merchantId);

	List<RiskRuleEntity> findByMerchantIdAndEnabledTrueOrderByPriorityAscIdAsc(UUID merchantId);

	List<RiskRuleEntity> findByMerchantIdAndRuleTypeOrderByPriorityAscIdAsc(UUID merchantId, RiskRuleType ruleType);

	Optional<RiskRuleEntity> findByIdAndMerchantId(UUID id, UUID merchantId);
}

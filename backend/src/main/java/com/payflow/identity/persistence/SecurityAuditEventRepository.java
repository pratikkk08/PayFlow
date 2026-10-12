package com.payflow.identity.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.identity.domain.SecurityAuditEventEntity;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEventEntity, UUID> {
}

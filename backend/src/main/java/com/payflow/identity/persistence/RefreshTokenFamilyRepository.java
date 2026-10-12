package com.payflow.identity.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.identity.domain.RefreshTokenFamilyEntity;

public interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamilyEntity, UUID> {
}

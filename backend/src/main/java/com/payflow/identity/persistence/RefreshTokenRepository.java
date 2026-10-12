package com.payflow.identity.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.identity.domain.RefreshTokenEntity;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from RefreshTokenEntity t where t.tokenHash = :hash")
	Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("hash") String hash);

	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}

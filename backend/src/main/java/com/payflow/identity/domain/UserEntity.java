package com.payflow.identity.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

	@Id
	private UUID id;

	@Column(name = "full_name", nullable = false, length = 200)
	private String fullName;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "email_normalized", nullable = false, length = 320)
	private String emailNormalized;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "role", nullable = false)
	@Enumerated(EnumType.STRING)
	private Set<PlatformRole> roles = new HashSet<>();

	protected UserEntity() {
	}

	public UserEntity(UUID id, String fullName, String email, String emailNormalized, String passwordHash,
			Instant createdAt, Set<PlatformRole> roles) {
		this.id = id;
		this.fullName = fullName;
		this.email = email;
		this.emailNormalized = emailNormalized;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
		this.roles = roles;
	}

	public UUID getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

	public String getEmailNormalized() {
		return emailNormalized;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Set<PlatformRole> getRoles() {
		return roles;
	}
}

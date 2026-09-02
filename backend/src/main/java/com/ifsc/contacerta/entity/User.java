package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AccountStatus status;

	@Column(name = "full_name", nullable = false, length = 160)
	private String fullName;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash")
	private String passwordHash;

	@Column(name = "registration_number", length = 80)
	private String registrationNumber;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "institution_id")
	private Institution institution;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public User(
			Role role,
			AccountStatus status,
			String fullName,
			String email,
			String registrationNumber,
			Institution institution
	) {
		this.id = UUID.randomUUID();
		this.role = role;
		this.status = status;
		this.fullName = fullName;
		this.email = email;
		this.registrationNumber = registrationNumber;
		this.institution = institution;
	}

	public void initializePassword(String hash, boolean mustChangePassword) {
		if (passwordHash != null) {
			throw new IllegalStateException("Password is already initialized.");
		}
		this.passwordHash = hash;
		this.mustChangePassword = mustChangePassword;
	}

	public void changePassword(String newPasswordHash) {
		passwordHash = newPasswordHash;
		mustChangePassword = false;
	}

	public void verifyEmail(Instant now) {
		emailVerifiedAt = now;
	}

	public void activate() {
		status = AccountStatus.ACTIVE;
	}

	public void updateFullName(String newFullName) {
		fullName = newFullName;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

}

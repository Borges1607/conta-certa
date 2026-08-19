package com.ifsc.contacerta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private AuthSession session;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "rotated_at")
	private Instant rotatedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "replaced_by_id", unique = true)
	private RefreshToken replacedBy;

	@Version
	@Column(nullable = false)
	private long version;

	public RefreshToken(AuthSession session, String tokenHash, Instant expiresAt, Instant now) {
		this.id = UUID.randomUUID();
		this.session = session;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.issuedAt = now;
	}

	public void rotateTo(RefreshToken successor, Instant now) {
		rotatedAt = now;
		replacedBy = successor;
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}
}

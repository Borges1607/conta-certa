package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.ActionTokenType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "action_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionToken {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ActionTokenType type;
	@Column(name = "token_hash", nullable = false, length = 64, unique = true) private String tokenHash;
	@Column(name = "expires_at", nullable = false) private Instant expiresAt;
	@Column(name = "consumed_at") private Instant consumedAt;
	@Column(name = "invalidated_at") private Instant invalidatedAt;
	@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

	public ActionToken(User user, ActionTokenType type, String tokenHash, Instant expiresAt, Instant now) {
		this.id = UUID.randomUUID(); this.user = user; this.type = type; this.tokenHash = tokenHash;
		this.expiresAt = expiresAt; this.createdAt = now;
	}
	public void consume(Instant now) { consumedAt = now; }
}

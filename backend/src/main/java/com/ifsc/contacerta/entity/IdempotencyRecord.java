package com.ifsc.contacerta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
	@Column(nullable = false, length = 16) private String method;
	@Column(name = "route_scope", nullable = false, length = 500) private String routeScope;
	@Column(nullable = false, length = 255) private String key;
	@Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
	@Column(name = "response_status", nullable = false) private int responseStatus;
	@Column(name = "response_content_type", nullable = false, length = 100) private String responseContentType;
	@Column(name = "response_location", length = 500) private String responseLocation;
	@Column(name = "response_body", nullable = false, columnDefinition = "text") private String responseBody;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "attempt_id") private Attempt attempt;
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(name = "expires_at", nullable = false) private Instant expiresAt;
	public IdempotencyRecord(User user, String method, String routeScope, String key, String requestHash, int responseStatus, String responseContentType, String responseLocation, String responseBody, Attempt attempt, Instant createdAt, Instant expiresAt) {
		this.id = UUID.randomUUID(); this.user = user; this.method = method; this.routeScope = routeScope; this.key = key; this.requestHash = requestHash; this.responseStatus = responseStatus; this.responseContentType = responseContentType; this.responseLocation = responseLocation; this.responseBody = responseBody; this.attempt = attempt; this.createdAt = createdAt; this.expiresAt = expiresAt;
	}
}

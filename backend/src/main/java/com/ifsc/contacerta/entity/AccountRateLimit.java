package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AccountRateLimitOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Entity
@Table(name = "account_rate_limits", uniqueConstraints = @UniqueConstraint(name = "uk_account_rate_limits_operation_subject", columnNames = {"operation", "subject_hash"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRateLimit {
	@Id private UUID id;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AccountRateLimitOperation operation;
	@Column(name = "subject_hash", nullable = false, length = 64) private String subjectHash;
	@Column(name = "window_started_at", nullable = false) private Instant windowStartedAt;
	@Column(name = "request_count", nullable = false) private int requestCount;
	@Version @Column(nullable = false) private long version;

	public AccountRateLimit(AccountRateLimitOperation operation, String subjectHash, Instant now) {
		this.id = UUID.randomUUID(); this.operation = operation; this.subjectHash = subjectHash;
		this.windowStartedAt = now; this.requestCount = 1;
	}
	public int increment(Instant now, Duration window) {
		if (!windowStartedAt.plus(window).isAfter(now)) { windowStartedAt = now; requestCount = 1; }
		else { requestCount++; }
		return requestCount;
	}
}

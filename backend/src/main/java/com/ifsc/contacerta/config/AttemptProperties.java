package com.ifsc.contacerta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.attempt")
public record AttemptProperties(Duration idempotencyTtl, int expirationBatchSize) {
	public AttemptProperties { if (idempotencyTtl == null) idempotencyTtl = Duration.ofHours(24); if (expirationBatchSize <= 0) expirationBatchSize = 100; }
}

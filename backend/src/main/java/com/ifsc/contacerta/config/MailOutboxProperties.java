package com.ifsc.contacerta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.mail")
public record MailOutboxProperties(String from, int batchSize, int maxAttempts, Duration claimLease, List<Duration> retryDelays) {
	public MailOutboxProperties {
		if (from == null || from.isBlank()) from = "Conta Certa <no-reply@contacerta.local>";
		if (batchSize <= 0) batchSize = 20;
		if (maxAttempts <= 0) maxAttempts = 5;
		if (claimLease == null) claimLease = Duration.ofMinutes(10);
		if (retryDelays == null || retryDelays.isEmpty()) retryDelays = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1));
	}
}

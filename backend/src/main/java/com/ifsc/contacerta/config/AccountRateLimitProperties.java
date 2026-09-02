package com.ifsc.contacerta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.account.rate-limit")
public record AccountRateLimitProperties(int allowance, Duration window) {
	public AccountRateLimitProperties {
		if (allowance <= 0) allowance = 5;
		if (window == null) window = Duration.ofHours(1);
	}
}

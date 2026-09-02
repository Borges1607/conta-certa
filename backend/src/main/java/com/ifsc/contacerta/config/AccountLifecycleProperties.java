package com.ifsc.contacerta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.account")
public record AccountLifecycleProperties(String frontendUrl, Token token) {
	public AccountLifecycleProperties {
		if (frontendUrl == null || frontendUrl.isBlank()) frontendUrl = "http://localhost:4200";
		if (token == null) token = new Token(null, null, null);
	}
	public record Token(Duration emailVerificationTtl, Duration teacherInvitationTtl, Duration passwordResetTtl) {
		public Token {
			if (emailVerificationTtl == null) emailVerificationTtl = Duration.ofHours(24);
			if (teacherInvitationTtl == null) teacherInvitationTtl = Duration.ofHours(72);
			if (passwordResetTtl == null) passwordResetTtl = Duration.ofMinutes(30);
		}
	}
}

package com.ifsc.contacerta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

@ConfigurationProperties("app.security")
public record SecurityProperties(
		Jwt jwt,
		Session session,
		Password password
) {
	public record Jwt(
			Resource privateKeyLocation,
			Resource publicKeyLocation,
			Duration accessTokenTtl
	) {
	}

	public record Session(Duration refreshTokenTtl) {
	}

	public record Password(int bcryptStrength) {
	}
}

package com.ifsc.contacerta.security;

import com.ifsc.contacerta.model.Role;

import java.time.Instant;
import java.util.UUID;

public record AccessTokenClaims(
		UUID userId,
		Role role,
		UUID sessionId,
		Instant issuedAt,
		Instant expiresAt,
		UUID jwtId
) {
}

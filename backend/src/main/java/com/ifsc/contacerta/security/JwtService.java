package com.ifsc.contacerta.security;

import com.ifsc.contacerta.config.SecurityProperties;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

	private final JwtEncoder encoder;
	private final JwtDecoder decoder;
	private final SecurityProperties properties;
	private final Clock clock;

	public String issue(UUID userId, Role role, UUID sessionId) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(userId.toString())
				.claim("role", role.name())
				.claim("sessionId", sessionId.toString())
				.issuedAt(now)
				.expiresAt(now.plus(properties.jwt().accessTokenTtl()))
				.id(UUID.randomUUID().toString())
				.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public AccessTokenClaims parse(String token) {
		try {
			Jwt jwt = decoder.decode(token);
			return new AccessTokenClaims(
					UUID.fromString(required(jwt.getSubject())),
					Role.valueOf(required(jwt.getClaimAsString("role"))),
					UUID.fromString(required(jwt.getClaimAsString("sessionId"))),
					jwt.getIssuedAt(),
					jwt.getExpiresAt(),
					UUID.fromString(required(jwt.getId()))
			);
		} catch (JwtException | IllegalArgumentException exception) {
			throw invalidAccessToken();
		}
	}

	private String required(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Required JWT claim is missing.");
		}
		return value;
	}

	private ApiException invalidAccessToken() {
		return new ApiException(
				HttpStatus.UNAUTHORIZED,
				"INVALID_ACCESS_TOKEN",
				"Access token is invalid or expired."
		);
	}
}

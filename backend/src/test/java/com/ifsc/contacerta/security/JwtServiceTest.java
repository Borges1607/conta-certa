package com.ifsc.contacerta.security;

import com.ifsc.contacerta.config.SecurityProperties;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.Role;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-19T18:00:00Z");
	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	private KeyPair keyPair;
	private SecurityProperties properties;

	@BeforeEach
	void setUp() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		keyPair = generator.generateKeyPair();
		properties = new SecurityProperties(
				new SecurityProperties.Jwt(null, null, Duration.ofMinutes(15)),
				new SecurityProperties.Session(Duration.ofDays(7)),
				new SecurityProperties.Password(12)
		);
	}

	@Test
	void deveEmitirEValidarJwtRs256ComClaimsObrigatorias() {
		Clock clock = fixedClock(NOW);
		JwtService jwtService = service(keyPair, clock);

		String token = jwtService.issue(USER_ID, Role.ADMIN, SESSION_ID);
		AccessTokenClaims claims = jwtService.parse(token);

		assertThat(claims.userId()).isEqualTo(USER_ID);
		assertThat(claims.role()).isEqualTo(Role.ADMIN);
		assertThat(claims.sessionId()).isEqualTo(SESSION_ID);
		assertThat(claims.issuedAt()).isEqualTo(NOW);
		assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		assertThat(claims.jwtId()).isNotNull();
	}

	@Test
	void deveRejeitarJwtExpirado() {
		String token = service(keyPair, fixedClock(NOW)).issue(USER_ID, Role.ADMIN, SESSION_ID);
		JwtService expiredClockService = service(keyPair, fixedClock(NOW.plus(Duration.ofMinutes(16))));

		assertThatThrownBy(() -> expiredClockService.parse(token))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveRejeitarJwtAssinadoPorOutraChave() throws Exception {
		String token = service(keyPair, fixedClock(NOW)).issue(USER_ID, Role.ADMIN, SESSION_ID);
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair anotherKeyPair = generator.generateKeyPair();

		assertThatThrownBy(() -> service(anotherKeyPair, fixedClock(NOW)).parse(token))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("INVALID_ACCESS_TOKEN"));
	}

	private JwtService service(KeyPair pair, Clock clock) {
		RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
		RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
		JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
		timestampValidator.setClock(clock);
		decoder.setJwtValidator(timestampValidator);
		return new JwtService(encoder, decoder, properties, clock);
	}

	private Clock fixedClock(Instant instant) {
		return Clock.fixed(instant, ZoneOffset.UTC);
	}
}

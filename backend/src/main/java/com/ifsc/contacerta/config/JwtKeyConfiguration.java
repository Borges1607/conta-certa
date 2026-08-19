package com.ifsc.contacerta.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtKeyConfiguration {

	@Bean
	@ConditionalOnMissingBean(RSAPrivateKey.class)
	RSAPrivateKey rsaPrivateKey(SecurityProperties properties) {
		try {
			byte[] encoded = readPem(properties.jwt().privateKeyLocation(), "PRIVATE KEY");
			return (RSAPrivateKey) KeyFactory.getInstance("RSA")
					.generatePrivate(new PKCS8EncodedKeySpec(encoded));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("JWT private key must be a valid PKCS#8 RSA key.", exception);
		}
	}

	@Bean
	@ConditionalOnMissingBean(RSAPublicKey.class)
	RSAPublicKey rsaPublicKey(SecurityProperties properties) {
		try {
			byte[] encoded = readPem(properties.jwt().publicKeyLocation(), "PUBLIC KEY");
			return (RSAPublicKey) KeyFactory.getInstance("RSA")
					.generatePublic(new X509EncodedKeySpec(encoded));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("JWT public key must be a valid X.509 RSA key.", exception);
		}
	}

	@Bean
	JwtEncoder jwtEncoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
		RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
	}

	@Bean
	JwtDecoder jwtDecoder(RSAPublicKey publicKey, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
		timestampValidator.setClock(clock);
		decoder.setJwtValidator(timestampValidator);
		return decoder;
	}

	private byte[] readPem(Resource resource, String label) {
		if (resource == null || !resource.exists() || !resource.isReadable()) {
			throw new IllegalStateException("JWT " + label.toLowerCase() + " resource is required and must be readable.");
		}

		try {
			String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			String encoded = pem
					.replace("-----BEGIN " + label + "-----", "")
					.replace("-----END " + label + "-----", "")
					.replaceAll("\\s", "");
			return Base64.getDecoder().decode(encoded);
		} catch (IOException | IllegalArgumentException exception) {
			throw new IllegalStateException("JWT " + label.toLowerCase() + " resource is invalid.", exception);
		}
	}
}

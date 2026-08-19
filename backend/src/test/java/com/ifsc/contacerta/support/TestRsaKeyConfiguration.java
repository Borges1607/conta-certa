package com.ifsc.contacerta.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@TestConfiguration(proxyBeanMethods = false)
public class TestRsaKeyConfiguration {

	private static final KeyPair KEY_PAIR = generateKeyPair();

	@Bean
	RSAPrivateKey testRsaPrivateKey() {
		return (RSAPrivateKey) KEY_PAIR.getPrivate();
	}

	@Bean
	RSAPublicKey testRsaPublicKey() {
		return (RSAPublicKey) KEY_PAIR.getPublic();
	}

	private static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("RSA must be available for tests.", exception);
		}
	}
}

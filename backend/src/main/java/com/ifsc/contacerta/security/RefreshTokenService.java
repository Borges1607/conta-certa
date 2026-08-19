package com.ifsc.contacerta.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.random.RandomGenerator;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private static final int TOKEN_BYTES = 32;

	private final RandomGenerator randomGenerator;

	public GeneratedRefreshToken generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		randomGenerator.nextBytes(bytes);
		String plainText = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new GeneratedRefreshToken(plainText, hash(plainText));
	}

	public String hash(String plainText) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(plainText.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 must be available.", exception);
		}
	}

	public record GeneratedRefreshToken(String plainText, String hash) {
	}
}

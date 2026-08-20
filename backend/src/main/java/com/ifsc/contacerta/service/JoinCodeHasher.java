package com.ifsc.contacerta.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class JoinCodeHasher {

	public String hash(String joinCode) {
		String normalizedCode = joinCode.trim().toUpperCase(Locale.ROOT);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(normalizedCode.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 must be available.", exception);
		}
	}
}

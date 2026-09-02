package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AccountLifecycleProperties;
import com.ifsc.contacerta.entity.ActionToken;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.ActionTokenType;
import com.ifsc.contacerta.repository.ActionTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class ActionTokenService {
	private final ActionTokenRepository repository;
	private final AccountLifecycleProperties properties;
	private final SecureRandom secureRandom;
	private final Clock clock;

	public GeneratedActionToken create(User user, ActionTokenType type) {
		Instant now = clock.instant();
		repository.invalidateUsableByUserIdAndType(user.getId(), type, now);
		byte[] bytes = new byte[32]; secureRandom.nextBytes(bytes);
		String plainText = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expiresAt = now.plus(ttl(type));
		repository.save(new ActionToken(user, type, hash(plainText), expiresAt, now));
		return new GeneratedActionToken(plainText, expiresAt);
	}

	public User consume(String plainText, ActionTokenType type) {
		if (plainText == null || plainText.isBlank()) throw notFound();
		ActionToken token = repository.findForUpdateByTokenHashAndType(hash(plainText), type).orElseThrow(this::notFound);
		Instant now = clock.instant();
		if (token.getConsumedAt() != null) throw new ApiException(HttpStatus.CONFLICT, "ACTION_TOKEN_USED", "Action token was already used.");
		if (token.getInvalidatedAt() != null) throw notFound();
		if (!token.getExpiresAt().isAfter(now)) throw new ApiException(HttpStatus.GONE, "ACTION_TOKEN_EXPIRED", "Action token has expired.");
		token.consume(now);
		return token.getUser();
	}
	private Duration ttl(ActionTokenType type) { return switch (type) {
		case EMAIL_VERIFICATION -> properties.token().emailVerificationTtl();
		case TEACHER_INVITATION -> properties.token().teacherInvitationTtl();
		case PASSWORD_RESET -> properties.token().passwordResetTtl();
	}; }
	private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
	private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "ACTION_TOKEN_NOT_FOUND", "Action token was not found."); }
	public record GeneratedActionToken(String plainText, Instant expiresAt) {}
}

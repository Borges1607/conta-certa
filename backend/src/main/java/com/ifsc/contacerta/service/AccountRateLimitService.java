package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AccountRateLimitProperties;
import com.ifsc.contacerta.entity.AccountRateLimit;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountRateLimitOperation;
import com.ifsc.contacerta.repository.AccountRateLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AccountRateLimitService {
	private final AccountRateLimitRepository repository;
	private final AccountRateLimitProperties properties;
	private final Clock clock;
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void check(String normalizedEmail, AccountRateLimitOperation operation) {
		Instant now = clock.instant(); String hash = hash(operation + ":" + normalizedEmail);
		var existing = repository.findByOperationAndSubjectHash(operation, hash);
		AccountRateLimit limit = existing.orElseGet(() -> repository.saveAndFlush(new AccountRateLimit(operation, hash, now)));
		int count = existing.isPresent() ? limit.increment(now, properties.window()) : 1;
		if (count > properties.allowance()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests. Try again later.");
	}
	private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
}

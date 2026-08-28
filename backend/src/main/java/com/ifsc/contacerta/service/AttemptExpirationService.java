package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AttemptProperties;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.repository.AttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AttemptExpirationService {

	private final AttemptRepository attemptRepository;
	private final AttemptExpirationFinalizer finalizer;
	private final AttemptProperties properties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.attempt.expiration-fixed-delay:60000}")
	public void expireDueAttempts() {
		Instant now = Instant.now(clock);
		attemptRepository.findExpiredIds(
				AttemptStatus.IN_PROGRESS,
				now,
				PageRequest.of(0, properties.expirationBatchSize())
		).forEach(finalizer::expire);
	}
}

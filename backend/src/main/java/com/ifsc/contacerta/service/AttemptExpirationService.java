package com.ifsc.contacerta.service;

import com.ifsc.contacerta.config.AttemptProperties;
import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.repository.AttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttemptExpirationService {

	private final AttemptRepository attemptRepository;
	private final AttemptFinalizationService finalizationService;
	private final AttemptProperties properties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.attempt.expiration-fixed-delay:60000}")
	public void expireDueAttempts() {
		Instant now = Instant.now(clock);
		attemptRepository.findExpiredIds(
				AttemptStatus.IN_PROGRESS,
				now,
				PageRequest.of(0, properties.expirationBatchSize())
		).forEach(this::expire);
	}

	@Transactional
	public boolean expire(UUID attemptId) {
		Attempt attempt = attemptRepository.findByIdForUpdate(attemptId).orElse(null);
		if (attempt == null || attempt.getStatus() != AttemptStatus.IN_PROGRESS
				|| attempt.getExpiresAt() == null || attempt.getExpiresAt().isAfter(Instant.now(clock))) {
			return false;
		}
		finalizationService.finalizeAttempt(attempt, AttemptStatus.EXPIRED, Instant.now(clock));
		return true;
	}
}

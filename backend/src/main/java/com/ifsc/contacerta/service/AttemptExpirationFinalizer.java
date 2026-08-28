package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.repository.AttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttemptExpirationFinalizer {
	private final AttemptRepository attemptRepository;
	private final AttemptFinalizationService finalizationService;
	private final Clock clock;

	@Transactional
	public boolean expire(UUID attemptId) {
		Attempt attempt = attemptRepository.findByIdForUpdate(attemptId).orElse(null);
		Instant now = Instant.now(clock);
		if (attempt == null || attempt.getStatus() != AttemptStatus.IN_PROGRESS
				|| attempt.getExpiresAt() == null || attempt.getExpiresAt().isAfter(now)) {
			return false;
		}
		finalizationService.finalizeAttempt(attempt, AttemptStatus.EXPIRED, now);
		return true;
	}
}

package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.RoomStudentProgress;
import com.ifsc.contacerta.model.AchievementCode;
import com.ifsc.contacerta.repository.AchievementUnlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AchievementUnlockService {

	private final AchievementUnlockRepository unlockRepository;

	public void evaluate(RoomStudentProgress progress, int scorePercent, Instant unlockedAt) {
		unlockWhen(progress, AchievementCode.FIRST_PASS, progress.getPassedAssignmentCount() >= 1, unlockedAt);
		unlockWhen(progress, AchievementCode.PERFECT_SCORE, scorePercent == 100, unlockedAt);
		unlockWhen(progress, AchievementCode.XP_100, progress.getTotalXp() >= 100, unlockedAt);
		unlockWhen(progress, AchievementCode.XP_500, progress.getTotalXp() >= 500, unlockedAt);
		unlockWhen(progress, AchievementCode.XP_1000, progress.getTotalXp() >= 1_000, unlockedAt);
		unlockWhen(progress, AchievementCode.PASSED_5, progress.getPassedAssignmentCount() >= 5, unlockedAt);
		unlockWhen(progress, AchievementCode.PASSED_10, progress.getPassedAssignmentCount() >= 10, unlockedAt);
	}

	private void unlockWhen(
			RoomStudentProgress progress,
			AchievementCode code,
			boolean satisfied,
			Instant unlockedAt
	) {
		if (!satisfied) {
			return;
		}
		unlockRepository.insertIfAbsent(
				UUID.randomUUID(), progress.getRoom().getId(), progress.getStudent().getId(), code.name(), unlockedAt
		);
	}
}

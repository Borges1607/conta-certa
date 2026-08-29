package com.ifsc.contacerta.dto.gamification;

import com.ifsc.contacerta.model.AchievementCode;

import java.time.Instant;

public record AchievementResponse(
		AchievementCode code,
		String title,
		String description,
		int current,
		int target,
		boolean unlocked,
		Instant unlockedAt
) {}

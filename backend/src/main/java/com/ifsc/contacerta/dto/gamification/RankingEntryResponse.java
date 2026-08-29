package com.ifsc.contacerta.dto.gamification;

import java.util.UUID;

public record RankingEntryResponse(
		long position,
		UUID studentId,
		String displayName,
		int totalXp,
		int totalStars,
		int level,
		boolean currentStudent
) {}

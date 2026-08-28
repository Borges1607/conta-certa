package com.ifsc.contacerta.dto.studentlesson;

public record LessonRulesResponse(
		Integer timeLimitMinutes,
		Integer maxAttempts,
		long attemptsUsed,
		Long attemptsRemaining,
		long questionCount,
		int passingScorePercent
) {}

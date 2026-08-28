package com.ifsc.contacerta.dto.studentlesson;

import com.ifsc.contacerta.model.AttemptAvailabilityStatus;
import com.ifsc.contacerta.model.LessonLockReason;

import java.time.Instant;
import java.util.UUID;

public record StudentLessonPathResponse(
		UUID assignmentId,
		UUID lessonId,
		String title,
		String summary,
		int order,
		AttemptAvailabilityStatus availability,
		LessonLockReason lockReason,
		Instant availableFrom,
		Instant dueAt,
		LessonRulesResponse rules,
		Integer bestScorePercent,
		Integer stars,
		UUID activeAttemptId,
		Instant activeAttemptExpiresAt,
		UUID bestAttemptId
) {}

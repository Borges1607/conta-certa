package com.ifsc.contacerta.dto.assignment;

import com.ifsc.contacerta.model.ContentStatus;

import java.time.Instant;
import java.util.UUID;

public record LessonAssignmentResponse(
		UUID id,
		UUID roomId,
		UUID lessonId,
		String lessonTitle,
		int position,
		ContentStatus status,
		Instant availableFrom,
		Instant dueAt,
		Integer timeLimitMinutes,
		Integer maxAttempts,
		Integer questionCount,
		boolean shuffleQuestions,
		boolean shuffleOptions,
		long activeQuestionCount,
		Instant createdAt,
		Instant updatedAt,
		long version
) {
}

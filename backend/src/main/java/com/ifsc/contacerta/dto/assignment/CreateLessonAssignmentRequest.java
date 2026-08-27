package com.ifsc.contacerta.dto.assignment;

import com.ifsc.contacerta.model.ContentStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record CreateLessonAssignmentRequest(
		@NotNull UUID lessonId,
		@Min(1) Integer position,
		ContentStatus status,
		Instant availableFrom,
		Instant dueAt,
		JsonNode timeLimitMinutes,
		JsonNode maxAttempts,
		JsonNode questionCount,
		Boolean shuffleQuestions,
		Boolean shuffleOptions
) {
}

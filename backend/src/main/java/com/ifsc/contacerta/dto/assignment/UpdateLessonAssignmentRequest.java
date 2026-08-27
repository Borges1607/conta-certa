package com.ifsc.contacerta.dto.assignment;

import com.ifsc.contacerta.model.ContentStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record UpdateLessonAssignmentRequest(
		ContentStatus status,
		JsonNode availableFrom,
		JsonNode dueAt,
		JsonNode timeLimitMinutes,
		JsonNode maxAttempts,
		JsonNode questionCount,
		Boolean shuffleQuestions,
		Boolean shuffleOptions,
		@NotNull @Min(0) Long version
) {
}

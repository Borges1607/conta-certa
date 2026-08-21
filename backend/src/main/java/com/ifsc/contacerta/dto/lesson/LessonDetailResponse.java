package com.ifsc.contacerta.dto.lesson;

import com.ifsc.contacerta.model.ContentStatus;

import java.time.Instant;
import java.util.UUID;

public record LessonDetailResponse(
		UUID id,
		String title,
		String summary,
		String theoryMarkdown,
		ContentStatus status,
		long questionCount,
		long assignmentCount,
		Instant createdAt,
		Instant updatedAt,
		long version
) {
}

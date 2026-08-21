package com.ifsc.contacerta.dto.lesson;

import com.ifsc.contacerta.model.ContentStatus;

import java.time.Instant;
import java.util.UUID;

public record LessonSummaryResponse(UUID id, String title, String summary, ContentStatus status, long questionCount, long assignmentCount, Instant createdAt, Instant updatedAt, long version) {
}

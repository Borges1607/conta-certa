package com.ifsc.contacerta.dto.studentlesson;

import com.ifsc.contacerta.model.AttemptAvailabilityStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentLessonDetailResponse(
		UUID assignmentId, UUID lessonId, String title, String summary, String theoryMarkdown,
		int position, AttemptAvailabilityStatus availability, Instant availableFrom, Instant dueAt,
		Integer timeLimitMinutes, Integer maxAttempts, Integer questionCount
) {}

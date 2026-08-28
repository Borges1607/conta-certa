package com.ifsc.contacerta.dto.studentlesson;

import com.ifsc.contacerta.model.AttemptAvailabilityStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentLessonPathResponse(
		UUID assignmentId, UUID lessonId, String title, String summary, int position,
		AttemptAvailabilityStatus availability, long attemptsUsed, Long attemptsAvailable,
		Integer bestScorePercent, boolean resumable, Instant dueAt
) {}

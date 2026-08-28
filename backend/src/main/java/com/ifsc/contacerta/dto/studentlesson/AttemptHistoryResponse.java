package com.ifsc.contacerta.dto.studentlesson;

import com.ifsc.contacerta.model.AttemptStatus;

import java.time.Instant;
import java.util.UUID;

public record AttemptHistoryResponse(
		UUID attemptId,
		AttemptStatus status,
		Instant startedAt,
		Instant submittedAt,
		Integer scorePercent,
		Integer stars,
		boolean passed,
		int correctAnswers,
		int totalQuestions,
		boolean best
) {}

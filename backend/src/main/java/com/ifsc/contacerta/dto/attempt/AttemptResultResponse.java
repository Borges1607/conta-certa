package com.ifsc.contacerta.dto.attempt;

import com.ifsc.contacerta.model.AttemptStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttemptResultResponse(UUID id, AttemptStatus status, int totalQuestions, int answeredQuestions,
		int correctAnswers, int scorePercent, boolean passed, int stars, int xpCredited, Instant submittedAt,
		List<AttemptAnswerReviewResponse> review) {}

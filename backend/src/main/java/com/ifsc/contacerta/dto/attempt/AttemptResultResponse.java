package com.ifsc.contacerta.dto.attempt;

import com.ifsc.contacerta.model.AttemptStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttemptResultResponse(
		UUID attemptId,
		UUID assignmentId,
		UUID roomId,
		UUID lessonId,
		String lessonTitle,
		AttemptStatus status,
		int correctAnswers,
		int totalQuestions,
		int scorePercent,
		boolean passed,
		int stars,
		int xpEarnedThisAttempt,
		int roomXpTotal,
		Instant startedAt,
		Instant submittedAt,
		int passingScorePercent,
		Long attemptsRemaining,
		List<AttemptAnswerReviewResponse> answers
) {
}

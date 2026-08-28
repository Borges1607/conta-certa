package com.ifsc.contacerta.dto.attempt;

import com.ifsc.contacerta.model.AttemptStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttemptResponse(
		UUID attemptId,
		UUID assignmentId,
		UUID roomId,
		UUID lessonId,
		String lessonTitle,
		AttemptStatus status,
		Instant startedAt,
		Instant expiresAt,
		Integer timeLimitMinutes,
		List<AttemptQuestionResponse> questions,
		List<RecordedAttemptAnswerResponse> answers,
		int passingScorePercent
) {
}

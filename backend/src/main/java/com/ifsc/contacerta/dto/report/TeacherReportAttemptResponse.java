package com.ifsc.contacerta.dto.report;

import com.ifsc.contacerta.model.AttemptStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeacherReportAttemptResponse(
		UUID attemptId,
		UUID lessonId,
		String lessonTitle,
		UUID assignmentId,
		int sequence,
		AttemptStatus status,
		Instant startedAt,
		Instant submittedAt,
		long durationSeconds,
		int totalQuestions,
		int answeredQuestions,
		int correctAnswers,
		int scorePercent,
		boolean passed,
		int starsEarned,
		int xpCredited,
		List<TeacherReportAttemptAnswerResponse> answers
) { }

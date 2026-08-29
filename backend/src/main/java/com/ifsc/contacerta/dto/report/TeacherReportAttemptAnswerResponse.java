package com.ifsc.contacerta.dto.report;

import com.ifsc.contacerta.dto.attempt.AttemptAnswerValueResponse;
import com.ifsc.contacerta.model.QuestionType;

import java.time.Instant;
import java.util.UUID;

public record TeacherReportAttemptAnswerResponse(
		UUID questionSnapshotId,
		int position,
		String prompt,
		QuestionType type,
		AttemptAnswerValueResponse recordedAnswer,
		boolean correct,
		AttemptAnswerValueResponse answerKey,
		String explanation,
		Instant answeredAt
) { }

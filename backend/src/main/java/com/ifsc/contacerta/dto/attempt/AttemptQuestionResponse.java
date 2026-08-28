package com.ifsc.contacerta.dto.attempt;

import com.ifsc.contacerta.model.QuestionType;

import java.util.List;
import java.util.UUID;

public record AttemptQuestionResponse(
		UUID questionSnapshotId,
		QuestionType type,
		String prompt,
		int order,
		List<AttemptOptionResponse> options,
		AttemptNumericSpecResponse numeric
) {
}

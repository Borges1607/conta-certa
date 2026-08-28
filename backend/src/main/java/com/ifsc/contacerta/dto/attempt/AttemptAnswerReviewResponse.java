package com.ifsc.contacerta.dto.attempt;

import com.ifsc.contacerta.model.QuestionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AttemptAnswerReviewResponse(UUID snapshotId, QuestionType type, String prompt, String explanation,
		boolean correct, List<UUID> selectedOptionIds, List<UUID> correctOptionIds, Boolean booleanValue,
		Boolean correctBoolean, BigDecimal numericValue, BigDecimal correctNumericValue) {}

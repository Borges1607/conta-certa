package com.ifsc.contacerta.dto.question;

import com.ifsc.contacerta.model.NumericUnit;
import com.ifsc.contacerta.model.QuestionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuestionResponse(UUID id, UUID lessonId, String prompt, QuestionType type, String explanation, int order, boolean archived, long version, List<QuestionOptionResponse> options, Boolean correctBoolean, BigDecimal correctNumericValue, BigDecimal absoluteTolerance, NumericUnit unit, Integer decimalPlaces) {
}

package com.ifsc.contacerta.dto.question;

import com.ifsc.contacerta.model.NumericUnit;
import com.ifsc.contacerta.model.QuestionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record UpdateQuestionRequest(String prompt, QuestionType type, String explanation, List<QuestionOptionRequest> options, Boolean correctBoolean, BigDecimal correctNumericValue, BigDecimal absoluteTolerance, NumericUnit unit, Integer decimalPlaces, @NotNull @Min(0) Long version) {
}

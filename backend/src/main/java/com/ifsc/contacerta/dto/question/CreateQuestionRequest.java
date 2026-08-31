package com.ifsc.contacerta.dto.question;

import com.ifsc.contacerta.model.NumericUnit;
import com.ifsc.contacerta.model.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record CreateQuestionRequest(
		@NotBlank String prompt,
		@NotNull QuestionType type,
		String explanation,
		List<@Valid QuestionOptionRequest> options,
		Boolean correctBoolean,
		@Digits(integer = 13, fraction = 6) BigDecimal correctNumericValue,
		@Digits(integer = 13, fraction = 6) @PositiveOrZero BigDecimal absoluteTolerance,
		NumericUnit unit,
		@PositiveOrZero Integer decimalPlaces
) {
}

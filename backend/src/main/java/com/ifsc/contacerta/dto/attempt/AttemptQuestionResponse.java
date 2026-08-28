package com.ifsc.contacerta.dto.attempt;
import com.ifsc.contacerta.model.NumericUnit; import com.ifsc.contacerta.model.QuestionType; import java.util.List; import java.util.UUID;
public record AttemptQuestionResponse(UUID id, QuestionType type, String prompt, int position, NumericUnit unit, Integer decimalPlaces, List<AttemptOptionResponse> options, AttemptAnswerValueResponse answer) {}

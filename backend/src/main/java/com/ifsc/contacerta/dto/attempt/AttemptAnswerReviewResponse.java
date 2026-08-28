package com.ifsc.contacerta.dto.attempt;

public record AttemptAnswerReviewResponse(
		AttemptQuestionResponse question,
		AttemptAnswerValueResponse studentAnswer,
		AttemptAnswerValueResponse correctAnswer,
		boolean correct,
		String explanation
) {
}

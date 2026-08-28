package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.dto.attempt.AttemptAnswerValueResponse;
import com.ifsc.contacerta.dto.attempt.AttemptNumericSpecResponse;
import com.ifsc.contacerta.dto.attempt.AttemptOptionResponse;
import com.ifsc.contacerta.dto.attempt.AttemptQuestionResponse;
import com.ifsc.contacerta.dto.attempt.AttemptResponse;
import com.ifsc.contacerta.dto.attempt.RecordedAttemptAnswerResponse;
import com.ifsc.contacerta.entity.Attempt;
import com.ifsc.contacerta.entity.AttemptAnswer;
import com.ifsc.contacerta.entity.AttemptQuestionSnapshot;
import com.ifsc.contacerta.model.QuestionType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AttemptMapper {

	public AttemptResponse toPublicResponse(Attempt attempt, List<AttemptAnswer> answers) {
		Map<UUID, AttemptAnswer> answersBySnapshot = answers.stream().collect(Collectors.toMap(
				answer -> answer.getQuestionSnapshot().getId(),
				answer -> answer
		));
		return new AttemptResponse(
				attempt.getId(),
				attempt.getAssignment().getId(),
				attempt.getAssignment().getRoom().getId(),
				attempt.getAssignment().getLesson().getId(),
				attempt.getAssignment().getLesson().getTitle(),
				attempt.getStatus(),
				attempt.getStartedAt(),
				attempt.getExpiresAt(),
				attempt.getAssignment().getTimeLimitMinutes(),
				attempt.getSnapshots().stream().map(this::toQuestion).toList(),
				attempt.getSnapshots().stream()
						.map(snapshot -> answersBySnapshot.get(snapshot.getId()))
						.filter(answer -> answer != null)
						.map(this::toRecordedAnswer)
						.toList(),
				attempt.getAssignment().getRoom().getPassingScorePercent()
		);
	}

	public AttemptQuestionResponse toQuestion(AttemptQuestionSnapshot snapshot) {
		AttemptNumericSpecResponse numeric = snapshot.getType() == QuestionType.NUMERIC
				? new AttemptNumericSpecResponse(snapshot.getUnit(), snapshot.getDecimalPlaces())
				: null;
		return new AttemptQuestionResponse(
				snapshot.getId(),
				snapshot.getType(),
				snapshot.getPrompt(),
				snapshot.getPosition(),
				snapshot.getOptions().stream()
						.map(option -> new AttemptOptionResponse(option.getId(), option.getText()))
						.toList(),
				numeric
		);
	}

	private RecordedAttemptAnswerResponse toRecordedAnswer(AttemptAnswer answer) {
		return new RecordedAttemptAnswerResponse(
				answer.getQuestionSnapshot().getId(),
				answer.getAnsweredAt(),
				toAnswerValue(answer)
		);
	}

	public AttemptAnswerValueResponse toAnswerValue(AttemptAnswer answer) {
		List<UUID> selectedOptionIds = answer.getSelectedOptions().isEmpty()
				? null
				: answer.getSelectedOptions().stream().map(option -> option.getId()).toList();
		return new AttemptAnswerValueResponse(
				selectedOptionIds,
				answer.getBooleanValue(),
				answer.getNumericValue() == null ? null : answer.getNumericValue().toPlainString()
		);
	}
}

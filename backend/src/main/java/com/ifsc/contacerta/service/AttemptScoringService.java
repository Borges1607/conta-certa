package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.attempt.RecordAttemptAnswerRequest;
import com.ifsc.contacerta.entity.AttemptOptionSnapshot;
import com.ifsc.contacerta.entity.AttemptQuestionSnapshot;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.QuestionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AttemptScoringService {
	public record ScoredAnswer(Set<AttemptOptionSnapshot> selectedOptions, Boolean booleanValue, BigDecimal numericValue, boolean correct) {}
	public ScoredAnswer validateAndScore(AttemptQuestionSnapshot snapshot, RecordAttemptAnswerRequest request) {
		int shapes = (request.selectedOptionIds() != null ? 1 : 0) + (request.booleanValue() != null ? 1 : 0) + (request.numericValue() != null ? 1 : 0);
		if (shapes != 1) throw invalid();
		return switch (snapshot.getType()) {
			case SINGLE_CHOICE, MULTIPLE_CHOICE -> scoreChoice(snapshot, request.selectedOptionIds());
			case TRUE_FALSE -> new ScoredAnswer(Set.of(), request.booleanValue(), null, request.booleanValue().equals(snapshot.getCorrectBoolean()));
			case NUMERIC -> scoreNumeric(snapshot, request.numericValue());
		};
	}
	private ScoredAnswer scoreChoice(AttemptQuestionSnapshot snapshot, List<UUID> ids) {
		if (ids == null || ids.size() != new LinkedHashSet<>(ids).size()) throw invalid();
		Set<UUID> selectedIds = Set.copyOf(ids); Set<AttemptOptionSnapshot> selected = new LinkedHashSet<>();
		for (AttemptOptionSnapshot option : snapshot.getOptions()) if (selectedIds.contains(option.getId())) selected.add(option);
		if (selected.size() != ids.size()) throw invalid();
		boolean correct = snapshot.getType() == QuestionType.SINGLE_CHOICE
				? selected.size() == 1 && selected.iterator().next().isCorrect()
				: selected.stream().filter(AttemptOptionSnapshot::isCorrect).count() == snapshot.getOptions().stream().filter(AttemptOptionSnapshot::isCorrect).count() && selected.stream().allMatch(AttemptOptionSnapshot::isCorrect);
		return new ScoredAnswer(Set.copyOf(selected), null, null, correct);
	}
	private ScoredAnswer scoreNumeric(AttemptQuestionSnapshot snapshot, String value) {
		try { BigDecimal numeric = new BigDecimal(value.replace(',', '.')); boolean correct = numeric.subtract(snapshot.getCorrectNumericValue()).abs().compareTo(snapshot.getAbsoluteTolerance()) <= 0; return new ScoredAnswer(Set.of(), null, numeric, correct); }
		catch (RuntimeException exception) { throw invalid(); }
	}
	private ApiException invalid() { return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ANSWER", "Invalid answer payload."); }
}

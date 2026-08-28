package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.attempt.RecordAttemptAnswerRequest;
import com.ifsc.contacerta.entity.AttemptOptionSnapshot;
import com.ifsc.contacerta.entity.AttemptQuestionSnapshot;
import com.ifsc.contacerta.model.QuestionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttemptScoringServiceTest {

	private final AttemptScoringService service = new AttemptScoringService();

	@Test
	void deveCorrigirEscolhaUnica() {
		AttemptOptionSnapshot correct = option(true);
		AttemptOptionSnapshot incorrect = option(false);
		AttemptScoringService.ScoredAnswer answer = service.validateAndScore(snapshot(QuestionType.SINGLE_CHOICE, List.of(correct, incorrect)), new RecordAttemptAnswerRequest(List.of(correct.getId()), null, null));
		assertThat(answer.correct()).isTrue();
	}

	@Test
	void deveExigirConjuntoExatoNaMultiplaEscolha() {
		AttemptOptionSnapshot first = option(true); AttemptOptionSnapshot second = option(true); AttemptOptionSnapshot extra = option(false);
		assertThat(service.validateAndScore(snapshot(QuestionType.MULTIPLE_CHOICE, List.of(first, second, extra)), new RecordAttemptAnswerRequest(List.of(first.getId(), second.getId()), null, null)).correct()).isTrue();
		assertThat(service.validateAndScore(snapshot(QuestionType.MULTIPLE_CHOICE, List.of(first, second, extra)), new RecordAttemptAnswerRequest(List.of(first.getId()), null, null)).correct()).isFalse();
	}

	@Test
	void deveAceitarVirgulaNaRespostaNumerica() {
		AttemptScoringService.ScoredAnswer answer = service.validateAndScore(numericSnapshot("100.00", "0.50"), new RecordAttemptAnswerRequest(null, null, "100,50"));
		assertThat(answer.correct()).isTrue();
		assertThat(answer.numericValue()).isEqualByComparingTo("100.50");
	}

	@Test
	void deveRejeitarPayloadIncompativel() {
		assertThatThrownBy(() -> service.validateAndScore(numericSnapshot("10", "0"), new RecordAttemptAnswerRequest(List.of(UUID.randomUUID()), null, "10"))).hasMessageContaining("Invalid");
	}

	private AttemptQuestionSnapshot snapshot(QuestionType type, List<AttemptOptionSnapshot> options) {
		AttemptQuestionSnapshot snapshot = mock(AttemptQuestionSnapshot.class); when(snapshot.getType()).thenReturn(type); when(snapshot.getOptions()).thenReturn(options); return snapshot;
	}
	private AttemptQuestionSnapshot numericSnapshot(String value, String tolerance) {
		AttemptQuestionSnapshot snapshot = snapshot(QuestionType.NUMERIC, List.of()); when(snapshot.getCorrectNumericValue()).thenReturn(new BigDecimal(value)); when(snapshot.getAbsoluteTolerance()).thenReturn(new BigDecimal(tolerance)); return snapshot;
	}
	private AttemptOptionSnapshot option(boolean correct) { AttemptOptionSnapshot option = mock(AttemptOptionSnapshot.class); when(option.getId()).thenReturn(UUID.randomUUID()); when(option.isCorrect()).thenReturn(correct); return option; }
}

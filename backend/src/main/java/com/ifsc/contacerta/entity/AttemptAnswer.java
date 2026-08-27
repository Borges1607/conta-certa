package com.ifsc.contacerta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "attempt_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttemptAnswer {

	@Id
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_snapshot_id", nullable = false, unique = true)
	private AttemptQuestionSnapshot questionSnapshot;

	@ManyToMany
	@JoinTable(
			name = "attempt_answer_selected_options",
			joinColumns = @JoinColumn(name = "answer_id"),
			inverseJoinColumns = @JoinColumn(name = "option_snapshot_id")
	)
	private Set<AttemptOptionSnapshot> selectedOptions = new LinkedHashSet<>();

	@Column(name = "boolean_value")
	private Boolean booleanValue;

	@Column(name = "numeric_value", precision = 19, scale = 6)
	private BigDecimal numericValue;

	@Column(nullable = false)
	private boolean correct;

	@Column(name = "answered_at", nullable = false)
	private Instant answeredAt;

	private AttemptAnswer(AttemptQuestionSnapshot snapshot, boolean correct, Instant answeredAt) {
		this.id = UUID.randomUUID();
		this.questionSnapshot = snapshot;
		this.correct = correct;
		this.answeredAt = answeredAt;
	}

	public static AttemptAnswer choice(
			AttemptQuestionSnapshot snapshot,
			Set<AttemptOptionSnapshot> selectedOptions,
			boolean correct,
			Instant answeredAt
	) {
		AttemptAnswer answer = new AttemptAnswer(snapshot, correct, answeredAt);
		answer.selectedOptions.addAll(selectedOptions);
		return answer;
	}

	public static AttemptAnswer booleanAnswer(
			AttemptQuestionSnapshot snapshot,
			boolean value,
			boolean correct,
			Instant answeredAt
	) {
		AttemptAnswer answer = new AttemptAnswer(snapshot, correct, answeredAt);
		answer.booleanValue = value;
		return answer;
	}

	public static AttemptAnswer numeric(
			AttemptQuestionSnapshot snapshot,
			BigDecimal value,
			boolean correct,
			Instant answeredAt
	) {
		AttemptAnswer answer = new AttemptAnswer(snapshot, correct, answeredAt);
		answer.numericValue = value;
		return answer;
	}

	public Set<AttemptOptionSnapshot> getSelectedOptions() {
		return Set.copyOf(selectedOptions);
	}
}

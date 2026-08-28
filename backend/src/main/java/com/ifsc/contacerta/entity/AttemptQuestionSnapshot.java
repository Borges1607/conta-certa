package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.NumericUnit;
import com.ifsc.contacerta.model.QuestionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "attempt_question_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttemptQuestionSnapshot {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "attempt_id", nullable = false)
	private Attempt attempt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false)
	private Question question;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private QuestionType type;

	@Column(nullable = false, columnDefinition = "text")
	private String prompt;

	@Column(columnDefinition = "text")
	private String explanation;

	@Column(nullable = false)
	private int position;

	@Column(name = "correct_boolean")
	private Boolean correctBoolean;

	@Column(name = "correct_numeric_value", precision = 19, scale = 6)
	private BigDecimal correctNumericValue;

	@Column(name = "absolute_tolerance", precision = 19, scale = 6)
	private BigDecimal absoluteTolerance;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private NumericUnit unit;

	@Column(name = "decimal_places")
	private Integer decimalPlaces;

	@Getter(AccessLevel.NONE)
	@OneToMany(mappedBy = "questionSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<AttemptOptionSnapshot> options = new ArrayList<>();

	AttemptQuestionSnapshot(Attempt attempt, Question question, int position, List<QuestionOption> sourceOptions) {
		this.id = UUID.randomUUID();
		this.attempt = attempt;
		this.question = question;
		this.type = question.getType();
		this.prompt = question.getPrompt();
		this.explanation = question.getExplanation();
		this.position = position;
		this.correctBoolean = question.getCorrectBoolean();
		this.correctNumericValue = question.getCorrectNumericValue();
		this.absoluteTolerance = question.getAbsoluteTolerance();
		this.unit = question.getUnit();
		this.decimalPlaces = question.getDecimalPlaces();
		for (int index = 0; index < sourceOptions.size(); index++) {
			options.add(new AttemptOptionSnapshot(this, sourceOptions.get(index), index + 1));
		}
	}

	public List<AttemptOptionSnapshot> getOptions() {
		return List.copyOf(options);
	}
}

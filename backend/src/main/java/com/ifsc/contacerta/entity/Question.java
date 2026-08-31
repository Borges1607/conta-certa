package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.NumericUnit;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lesson_id", nullable = false)
	private Lesson lesson;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private QuestionType type;

	@Column(nullable = false, columnDefinition = "text")
	private String prompt;

	@Column(columnDefinition = "text")
	private String explanation;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false)
	private boolean active;

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
	@OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<QuestionOption> options = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	private Question(
			Lesson lesson,
			QuestionType type,
			String prompt,
			String explanation,
			List<QuestionOptionData> optionData,
			int position
	) {
		this.id = UUID.randomUUID();
		this.lesson = lesson;
		this.type = type;
		this.prompt = prompt;
		this.explanation = explanation;
		this.position = position;
		this.active = true;
		for (int index = 0; index < optionData.size(); index++) {
			QuestionOptionData option = optionData.get(index);
			options.add(new QuestionOption(this, option.text(), option.correct(), index + 1));
		}
	}

	public static Question choice(
			Lesson lesson,
			QuestionType type,
			String prompt,
			String explanation,
			List<QuestionOptionData> options
	) {
		return create(lesson, type, prompt, explanation, options, 1);
	}

	public static Question create(
			Lesson lesson,
			QuestionType type,
			String prompt,
			String explanation,
			List<QuestionOptionData> options,
			int position
	) {
		return new Question(lesson, type, prompt, explanation, options, position);
	}

	public void configureBoolean(boolean correctBoolean) {
		this.correctBoolean = correctBoolean;
	}

	public void configureNumeric(BigDecimal correctNumericValue, BigDecimal absoluteTolerance, NumericUnit unit, int decimalPlaces) {
		this.correctNumericValue = correctNumericValue;
		this.absoluteTolerance = absoluteTolerance;
		this.unit = unit;
		this.decimalPlaces = decimalPlaces;
	}

	public void moveTo(int position) {
		this.position = position;
	}

	public void archive() {
		active = false;
	}

	public void updatePromptAndExplanation(String prompt, String explanation) {
		this.prompt = prompt;
		this.explanation = explanation;
	}

	public void replaceConfiguration(
			QuestionType type,
			String prompt,
			String explanation,
			List<QuestionOptionData> optionData,
			Boolean correctBoolean,
			BigDecimal correctNumericValue,
			BigDecimal absoluteTolerance,
			NumericUnit unit,
			Integer decimalPlaces
	) {
		this.type = type;
		this.prompt = prompt;
		this.explanation = explanation;
		replaceOptions(optionData);
		this.correctBoolean = type == QuestionType.TRUE_FALSE ? correctBoolean : null;
		this.correctNumericValue = type == QuestionType.NUMERIC ? correctNumericValue : null;
		this.absoluteTolerance = type == QuestionType.NUMERIC ? absoluteTolerance : null;
		this.unit = type == QuestionType.NUMERIC ? unit : null;
		this.decimalPlaces = type == QuestionType.NUMERIC ? decimalPlaces : null;
	}

	public Question duplicateInto(Lesson target, int targetPosition) {
		List<QuestionOptionData> copiedOptions = options.stream()
				.map(option -> new QuestionOptionData(option.getText(), option.isCorrect()))
				.toList();
		Question copy = create(target, type, prompt, explanation, copiedOptions, targetPosition);
		if (type == QuestionType.TRUE_FALSE) {
			copy.configureBoolean(correctBoolean);
		}
		if (type == QuestionType.NUMERIC) {
			copy.configureNumeric(correctNumericValue, absoluteTolerance, unit, decimalPlaces);
		}
		return copy;
	}

	private void replaceOptions(List<QuestionOptionData> optionData) {
		Map<UUID, QuestionOption> currentOptions = new HashMap<>();
		for (QuestionOption option : options) {
			currentOptions.put(option.getId(), option);
		}
		List<QuestionOption> replacement = new ArrayList<>();
		for (int index = 0; index < optionData.size(); index++) {
			QuestionOptionData data = optionData.get(index);
			QuestionOption option;
			if (data.id() == null) {
				option = new QuestionOption(this, data.text(), data.correct(), index + 1);
			} else {
				option = currentOptions.get(data.id());
				if (option == null) {
					throw new IllegalArgumentException("Question option does not belong to this question.");
				}
				option.update(data.text(), data.correct(), index + 1);
			}
			replacement.add(option);
		}
		options.clear();
		options.addAll(replacement);
	}

	public List<QuestionOption> getOptions() {
		return List.copyOf(options);
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}

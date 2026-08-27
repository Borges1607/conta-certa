package com.ifsc.contacerta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "attempt_option_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttemptOptionSnapshot {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_snapshot_id", nullable = false)
	private AttemptQuestionSnapshot questionSnapshot;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_option_id", nullable = false)
	private QuestionOption sourceOption;

	@Column(nullable = false, length = 500)
	private String text;

	@Column(nullable = false)
	private boolean correct;

	@Column(nullable = false)
	private int position;

	AttemptOptionSnapshot(AttemptQuestionSnapshot questionSnapshot, QuestionOption sourceOption, int position) {
		this.id = UUID.randomUUID();
		this.questionSnapshot = questionSnapshot;
		this.sourceOption = sourceOption;
		this.text = sourceOption.getText();
		this.correct = sourceOption.isCorrect();
		this.position = position;
	}
}

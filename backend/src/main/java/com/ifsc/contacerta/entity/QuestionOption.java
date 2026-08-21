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
@Table(name = "question_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionOption {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false)
	private Question question;

	@Column(nullable = false, length = 500)
	private String text;

	private boolean correct;

	@Column(nullable = false)
	private int position;

	QuestionOption(Question question, String text, boolean correct, int position) {
		this.id = UUID.randomUUID();
		this.question = question;
		this.text = text;
		this.correct = correct;
		this.position = position;
	}
}

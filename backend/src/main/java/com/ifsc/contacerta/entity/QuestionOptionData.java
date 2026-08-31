package com.ifsc.contacerta.entity;

import java.util.UUID;

public record QuestionOptionData(UUID id, String text, boolean correct) {

	public QuestionOptionData(String text, boolean correct) {
		this(null, text, correct);
	}
}

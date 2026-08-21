package com.ifsc.contacerta.dto.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record QuestionOptionRequest(
		UUID id,
		@NotBlank @Size(max = 500) String text,
		boolean correct
) {
}

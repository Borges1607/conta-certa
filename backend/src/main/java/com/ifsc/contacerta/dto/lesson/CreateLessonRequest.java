package com.ifsc.contacerta.dto.lesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLessonRequest(
		@NotBlank @Size(max = 160) String title,
		@Size(max = 500) String summary,
		@NotBlank String theoryMarkdown
) {
}

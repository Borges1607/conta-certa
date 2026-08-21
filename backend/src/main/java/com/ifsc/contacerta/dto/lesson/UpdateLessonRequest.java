package com.ifsc.contacerta.dto.lesson;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLessonRequest(
		@Size(min = 1, max = 160) String title,
		@Size(max = 500) String summary,
		String theoryMarkdown,
		@NotNull @Min(0) Long version
) {
}

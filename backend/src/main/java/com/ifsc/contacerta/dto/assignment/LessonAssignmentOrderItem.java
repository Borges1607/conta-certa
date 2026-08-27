package com.ifsc.contacerta.dto.assignment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LessonAssignmentOrderItem(
		@NotNull UUID assignmentId,
		@NotNull @Min(0) Long version
) {
}

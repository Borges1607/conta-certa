package com.ifsc.contacerta.dto.assignment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record LessonAssignmentOrderRequest(
		@NotEmpty List<@Valid LessonAssignmentOrderItem> assignments
) {
}

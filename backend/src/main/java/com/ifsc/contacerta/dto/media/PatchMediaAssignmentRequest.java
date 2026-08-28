package com.ifsc.contacerta.dto.media;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record PatchMediaAssignmentRequest(
		@NotNull @Min(0) Long version,
		JsonNode lessonAssignmentId,
		@Min(1) Integer position
) {
}

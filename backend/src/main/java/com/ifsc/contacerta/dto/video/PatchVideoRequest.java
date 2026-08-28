package com.ifsc.contacerta.dto.video;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record PatchVideoRequest(
		@NotNull @Min(0) Long version,
		@Size(min = 1, max = 160) String title,
		JsonNode description,
		JsonNode category,
		@Size(min = 1, max = 2048) String url
) {
}

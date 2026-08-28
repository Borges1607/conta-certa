package com.ifsc.contacerta.dto.material;

import com.ifsc.contacerta.model.MaterialKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record PatchMaterialRequest(
		@NotNull @Min(0) Long version,
		@Size(min = 1, max = 160) String title,
		JsonNode description,
		JsonNode category,
		MaterialKind kind,
		JsonNode url,
		JsonNode fileId
) {
}

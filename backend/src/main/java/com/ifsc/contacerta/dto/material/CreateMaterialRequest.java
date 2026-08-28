package com.ifsc.contacerta.dto.material;

import com.ifsc.contacerta.model.MaterialKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateMaterialRequest(
		@NotBlank @Size(max = 160) String title,
		@Size(max = 1000) String description,
		@Size(max = 120) String category,
		@NotNull MaterialKind kind,
		@Size(max = 2048) String url,
		UUID fileId
) {
}

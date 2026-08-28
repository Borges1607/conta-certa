package com.ifsc.contacerta.dto.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVideoRequest(
		@NotBlank @Size(max = 160) String title,
		@Size(max = 1000) String description,
		@Size(max = 120) String category,
		@NotBlank @Size(max = 2048) String url
) {
}

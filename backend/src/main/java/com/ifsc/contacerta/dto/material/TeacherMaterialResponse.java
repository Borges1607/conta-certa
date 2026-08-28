package com.ifsc.contacerta.dto.material;

import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MaterialKind;

import java.time.Instant;
import java.util.UUID;

public record TeacherMaterialResponse(
		UUID id,
		String title,
		String description,
		String category,
		MaterialKind kind,
		String url,
		MaterialFileResponse file,
		ContentStatus status,
		Instant createdAt,
		Instant updatedAt,
		long version
) {
}

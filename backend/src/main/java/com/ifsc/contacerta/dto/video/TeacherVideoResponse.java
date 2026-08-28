package com.ifsc.contacerta.dto.video;

import com.ifsc.contacerta.model.ContentStatus;

import java.time.Instant;
import java.util.UUID;

public record TeacherVideoResponse(
		UUID id,
		String title,
		String description,
		String category,
		String url,
		ContentStatus status,
		Instant createdAt,
		Instant updatedAt,
		long version
) {
}

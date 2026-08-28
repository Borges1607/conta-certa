package com.ifsc.contacerta.dto.media;

import com.ifsc.contacerta.model.MaterialKind;

import java.time.Instant;
import java.util.UUID;

public record StudentMaterialResponse(
		UUID id,
		String title,
		String description,
		MaterialKind kind,
		String externalUrl,
		UUID fileId,
		String fileName,
		Long fileSizeBytes,
		String contentType,
		MediaLessonLinkResponse lesson,
		boolean viewed,
		Instant firstViewedAt
) {
}

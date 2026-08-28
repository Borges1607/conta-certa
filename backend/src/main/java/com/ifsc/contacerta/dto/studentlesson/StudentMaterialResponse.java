package com.ifsc.contacerta.dto.studentlesson;

import java.time.Instant;
import java.util.UUID;

public record StudentMaterialResponse(
		UUID id,
		String title,
		String description,
		String kind,
		String externalUrl,
		UUID fileId,
		String fileName,
		Long fileSizeBytes,
		String contentType,
		Object lesson,
		boolean viewed,
		Instant firstViewedAt
) {}

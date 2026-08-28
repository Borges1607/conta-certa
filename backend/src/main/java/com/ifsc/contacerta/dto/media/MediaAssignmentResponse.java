package com.ifsc.contacerta.dto.media;

import com.ifsc.contacerta.model.MediaViewType;

import java.time.Instant;
import java.util.UUID;

public record MediaAssignmentResponse(
		UUID id,
		UUID roomId,
		MediaViewType mediaType,
		UUID mediaId,
		String title,
		UUID lessonAssignmentId,
		String lessonTitle,
		int position,
		Instant createdAt,
		long version
) {
}

package com.ifsc.contacerta.dto.media;

import java.time.Instant;
import java.util.UUID;

public record StudentVideoResponse(
		UUID id,
		String title,
		String description,
		String url,
		Integer durationMinutes,
		MediaLessonLinkResponse lesson,
		boolean viewed,
		Instant firstViewedAt
) {
}

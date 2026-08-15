package com.ifsc.contacerta.dto.room;

import com.ifsc.contacerta.model.Grade;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoomResponse(
		UUID id,
		UUID teacherId,
		UUID institutionId,
		String name,
		String description,
		Grade grade,
		List<String> contentTopics,
		int passingScorePercent,
		String joinCode,
		Instant archivedAt,
		Instant createdAt,
		Instant updatedAt,
		long version
) {
}

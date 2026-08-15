package com.ifsc.contacerta.dto.room;

import com.ifsc.contacerta.model.Grade;

import java.util.List;

public record CreateRoomRequest(
		String name,
		String description,
		Grade grade,
		List<String> contentTopics,
		Integer passingScorePercent
) {
}

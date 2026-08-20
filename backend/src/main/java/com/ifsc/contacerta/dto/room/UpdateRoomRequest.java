package com.ifsc.contacerta.dto.room;

import com.ifsc.contacerta.model.Grade;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record UpdateRoomRequest(
		String name,
		JsonNode description,
		Grade grade,
		List<String> contentTopics,
		Integer passingScorePercent,
		Long version
) {
}

package com.ifsc.contacerta.dto.room;

import com.ifsc.contacerta.model.Grade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record UpdateRoomRequest(
		@Size(min = 1, max = 160) String name,
		JsonNode description,
		Grade grade,
		List<@Size(min = 1, max = 120) String> contentTopics,
		@Min(0) @Max(100) Integer passingScorePercent,
		@NotNull @Min(0) Long version
) {
}

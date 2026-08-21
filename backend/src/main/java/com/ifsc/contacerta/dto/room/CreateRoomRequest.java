package com.ifsc.contacerta.dto.room;

import com.ifsc.contacerta.model.Grade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoomRequest(
		@NotBlank @Size(max = 160) String name,
		@Size(max = 1000) String description,
		@NotNull Grade grade,
		@NotEmpty List<@NotBlank @Size(max = 120) String> contentTopics,
		@Min(0) @Max(100) Integer passingScorePercent
) {
}

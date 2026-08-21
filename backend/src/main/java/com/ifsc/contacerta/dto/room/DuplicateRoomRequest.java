package com.ifsc.contacerta.dto.room;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DuplicateRoomRequest(
		@Size(min = 1, max = 160) String name,
		@NotNull @Min(0) Long version
) {
}

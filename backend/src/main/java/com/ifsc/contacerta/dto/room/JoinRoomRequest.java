package com.ifsc.contacerta.dto.room;

import jakarta.validation.constraints.Pattern;

public record JoinRoomRequest(
		@Pattern(regexp = "\\s*[A-Za-z0-9]{6}\\s*", message = "must contain six alphanumeric characters") String code
) {
}

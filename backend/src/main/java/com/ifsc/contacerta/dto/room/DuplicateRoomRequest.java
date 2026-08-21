package com.ifsc.contacerta.dto.room;

import jakarta.validation.constraints.Size;

public record DuplicateRoomRequest(@Size(min = 1, max = 160) String name) {
}

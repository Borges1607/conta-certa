package com.ifsc.contacerta.dto.room;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VersionRequest(@NotNull @Min(0) Long version) {
}

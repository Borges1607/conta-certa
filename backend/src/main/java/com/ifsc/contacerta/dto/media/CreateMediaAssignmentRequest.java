package com.ifsc.contacerta.dto.media;

import com.ifsc.contacerta.model.MediaViewType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMediaAssignmentRequest(
		@NotNull MediaViewType mediaType,
		@NotNull UUID mediaId,
		UUID lessonAssignmentId
) {
}

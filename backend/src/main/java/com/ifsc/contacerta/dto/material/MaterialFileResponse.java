package com.ifsc.contacerta.dto.material;

import java.util.UUID;

public record MaterialFileResponse(
		UUID id,
		String fileName,
		String contentType,
		long sizeBytes
) {}

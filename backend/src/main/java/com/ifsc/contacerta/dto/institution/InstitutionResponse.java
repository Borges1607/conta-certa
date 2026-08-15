package com.ifsc.contacerta.dto.institution;

import java.time.Instant;
import java.util.UUID;

public record InstitutionResponse(
		UUID id,
		String name,
		String cnpj,
		String contactEmail,
		String contactPhone,
		boolean active,
		Instant createdAt,
		Instant updatedAt,
		long version
) {
}

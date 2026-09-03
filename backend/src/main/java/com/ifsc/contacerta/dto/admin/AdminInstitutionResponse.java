package com.ifsc.contacerta.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminInstitutionResponse(
		UUID id,
		String name,
		String cnpj,
		String contactEmail,
		String contactPhone,
		boolean active,
		Instant createdAt,
		Instant updatedAt,
		long version,
		long teacherCount,
		long studentCount
) {
}

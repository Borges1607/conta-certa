package com.ifsc.contacerta.dto.institution;

import java.util.UUID;

public record InstitutionSummaryResponse(
		UUID id,
		String name,
		String cnpj,
		String contactEmail,
		String contactPhone,
		boolean active
) {
}

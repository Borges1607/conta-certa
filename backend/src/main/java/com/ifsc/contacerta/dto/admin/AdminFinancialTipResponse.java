package com.ifsc.contacerta.dto.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminFinancialTipResponse(
		UUID id,
		String title,
		String content,
		String sourceUrl,
		LocalDate publicationDate,
		boolean active,
		Instant createdAt,
		Instant updatedAt,
		long version,
		Instant archivedAt
) {
}

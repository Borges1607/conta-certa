package com.ifsc.contacerta.dto.studentdashboard;

import java.time.LocalDate;
import java.util.UUID;

public record StudentFinancialTipResponse(
		UUID id,
		String title,
		String content,
		String sourceUrl,
		LocalDate publicationDate
) {
}

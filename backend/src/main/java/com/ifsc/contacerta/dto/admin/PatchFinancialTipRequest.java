package com.ifsc.contacerta.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PatchFinancialTipRequest(
		@NotBlank @Size(max = 160) String title,
		@NotBlank String content,
		@Size(max = 2048) String sourceUrl,
		@NotNull LocalDate publicationDate,
		@NotNull Long version
) {
}

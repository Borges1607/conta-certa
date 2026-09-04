package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.entity.FinancialTip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AdminFinancialTipMapperTest {

	@Test
	void mapeiaTodosOsCamposSemAlterarMarkdownOuData() {
		FinancialTip tip = new FinancialTip(
				"Reserva de emergência",
				"## Comece pequeno\n\n**Poupe** todo mês.",
				"https://educacao.example.org/reserva",
				LocalDate.of(2026, 9, 3),
				true
		);

		var response = AdminFinancialTipMapper.toResponse(tip);

		assertThat(response.id()).isEqualTo(tip.getId());
		assertThat(response.title()).isEqualTo("Reserva de emergência");
		assertThat(response.content()).isEqualTo("## Comece pequeno\n\n**Poupe** todo mês.");
		assertThat(response.sourceUrl()).isEqualTo("https://educacao.example.org/reserva");
		assertThat(response.publicationDate()).isEqualTo(LocalDate.of(2026, 9, 3));
		assertThat(response.active()).isTrue();
		assertThat(response.version()).isZero();
		assertThat(response.archivedAt()).isNull();
	}
}

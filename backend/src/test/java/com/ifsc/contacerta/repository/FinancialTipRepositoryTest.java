package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.FinancialTip;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import com.ifsc.contacerta.specification.FinancialTipSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class FinancialTipRepositoryTest extends PostgresIntegrationTest {

	@Autowired
	private FinancialTipRepository repository;

	@Test
	void filtraPorBuscaStatusDataEExcluiArquivadas() {
		FinancialTip active = repository.save(new FinancialTip("Reserva de emergência", "Conteúdo", null, LocalDate.of(2026, 9, 3), true));
		repository.save(new FinancialTip("Outro assunto", "Conteúdo", null, LocalDate.of(2026, 9, 3), false));
		FinancialTip archived = repository.save(new FinancialTip("Reserva arquivada", "Conteúdo", null, LocalDate.of(2026, 9, 3), true));
		archived.archive(Instant.parse("2026-09-03T12:00:00Z"));
		repository.flush();

		var page = repository.findAll(FinancialTipSpecification.filtered("RESERVA", true, LocalDate.of(2026, 9, 3)), PageRequest.of(0, 20));

		assertThat(page.getContent()).containsExactly(active);
	}
}

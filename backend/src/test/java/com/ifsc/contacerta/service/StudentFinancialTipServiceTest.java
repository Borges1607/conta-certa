package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.studentdashboard.StudentFinancialTipResponse;
import com.ifsc.contacerta.entity.FinancialTip;
import com.ifsc.contacerta.repository.FinancialTipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentFinancialTipServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 4);

	@Mock private FinancialTipRepository financialTipRepository;

	private StudentFinancialTipService service;

	@BeforeEach
	void setUp() {
		service = new StudentFinancialTipService(
				financialTipRepository,
				Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC)
		);
	}

	@Test
	void devePriorizarDicaAgendadaParaDataLocalDeSaoPaulo() {
		FinancialTip scheduled = tip("Dica agendada", TODAY);
		FinancialTip fallback = tip("Dica de fallback", TODAY.minusDays(1));
		when(financialTipRepository.findByActiveTrueAndArchivedAtIsNullAndPublicationDateOrderByIdAsc(TODAY))
				.thenReturn(List.of(scheduled));

		StudentFinancialTipResponse response = service.currentTip();

		assertThat(response)
				.extracting(
						StudentFinancialTipResponse::id,
						StudentFinancialTipResponse::title,
						StudentFinancialTipResponse::content,
						StudentFinancialTipResponse::sourceUrl,
						StudentFinancialTipResponse::publicationDate
				)
				.containsExactly(scheduled.getId(), "Dica agendada", "Conteúdo", "https://example.com", TODAY);
	}

	@Test
	void deveUsarFallbackDeterministicoQuandoNaoHaDicaAgendada() {
		FinancialTip first = tip("Primeira", TODAY.minusDays(2));
		FinancialTip second = tip("Segunda", TODAY.minusDays(1));
		List<FinancialTip> tips = List.of(first, second);
		when(financialTipRepository.findByActiveTrueAndArchivedAtIsNullAndPublicationDateOrderByIdAsc(TODAY))
				.thenReturn(List.of());
		when(financialTipRepository.findByActiveTrueAndArchivedAtIsNullOrderByIdAsc()).thenReturn(tips);

		StudentFinancialTipResponse response = service.currentTip();

		FinancialTip expected = tips.get(Math.floorMod(TODAY.toEpochDay(), tips.size()));
		assertThat(response.id()).isEqualTo(expected.getId());
		assertThat(response.title()).isEqualTo(expected.getTitle());
	}

	@Test
	void deveRetornarNuloQuandoNaoHaDicasAtivas() {
		when(financialTipRepository.findByActiveTrueAndArchivedAtIsNullAndPublicationDateOrderByIdAsc(TODAY))
				.thenReturn(List.of());
		when(financialTipRepository.findByActiveTrueAndArchivedAtIsNullOrderByIdAsc()).thenReturn(List.of());

		assertThat(service.currentTip()).isNull();
	}

	private FinancialTip tip(String title, LocalDate publicationDate) {
		return new FinancialTip(title, "Conteúdo", "https://example.com", publicationDate, true);
	}
}

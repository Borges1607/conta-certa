package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.admin.AdminFinancialTipResponse;
import com.ifsc.contacerta.dto.admin.CreateFinancialTipRequest;
import com.ifsc.contacerta.dto.admin.PatchFinancialTipRequest;
import com.ifsc.contacerta.entity.FinancialTip;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.repository.FinancialTipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFinancialTipServiceTest {

	@Mock
	private FinancialTipRepository repository;

	private AdminFinancialTipService service;
	private Clock clock;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
		service = new AdminFinancialTipService(repository, clock);
	}

	@Test
	void criaInativaPorPadraoENormalizaCampos() {
		CreateFinancialTipRequest request = new CreateFinancialTipRequest(
				"  Reserva de emergência ", "  **Poupe**  ", " https://example.org/dica ", LocalDate.of(2026, 9, 3), null
		);
		when(repository.save(any(FinancialTip.class))).thenAnswer(invocation -> invocation.getArgument(0));

		AdminFinancialTipResponse response = service.create(request);

		assertThat(response.title()).isEqualTo("Reserva de emergência");
		assertThat(response.content()).isEqualTo("**Poupe**");
		assertThat(response.sourceUrl()).isEqualTo("https://example.org/dica");
		assertThat(response.active()).isFalse();
	}

	@Test
	void rejeitaUrlQueNaoSejaHttpOuHttps() {
		CreateFinancialTipRequest request = new CreateFinancialTipRequest(
				"Dica", "Conteúdo", "javascript:alert(1)", LocalDate.of(2026, 9, 3), false
		);

		assertThatThrownBy(() -> service.create(request))
				.isInstanceOf(ApiException.class)
				.satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("INVALID_SOURCE_URL"));
	}

	@Test
	void atualizaSomenteComVersaoAtual() {
		FinancialTip tip = new FinancialTip("Antiga", "Texto", null, LocalDate.of(2026, 9, 1), false);
		PatchFinancialTipRequest request = new PatchFinancialTipRequest("Nova", "Atualizado", null, LocalDate.of(2026, 9, 2), 0L);
		when(repository.findByIdAndArchivedAtIsNull(tip.getId())).thenReturn(Optional.of(tip));

		AdminFinancialTipResponse response = service.update(tip.getId(), request);

		assertThat(response.title()).isEqualTo("Nova");
		assertThat(response.content()).isEqualTo("Atualizado");
		assertThat(response.publicationDate()).isEqualTo(LocalDate.of(2026, 9, 2));
	}

	@Test
	void rejeitaAtualizacaoComVersaoObsoleta() {
		FinancialTip tip = new FinancialTip("Dica", "Texto", null, LocalDate.of(2026, 9, 1), false);
		PatchFinancialTipRequest request = new PatchFinancialTipRequest("Nova", "Texto", null, LocalDate.of(2026, 9, 2), 1L);
		when(repository.findByIdAndArchivedAtIsNull(tip.getId())).thenReturn(Optional.of(tip));

		assertThatThrownBy(() -> service.update(tip.getId(), request))
				.isInstanceOf(ApiException.class)
				.satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("VERSION_CONFLICT"));
	}

	@Test
	void arquivaComRelogioEExcluiDaConsultaPorId() {
		FinancialTip tip = new FinancialTip("Dica", "Texto", null, LocalDate.of(2026, 9, 1), true);
		when(repository.findByIdAndArchivedAtIsNull(tip.getId())).thenReturn(Optional.of(tip));

		service.archive(tip.getId());

		assertThat(tip.getArchivedAt()).isEqualTo(Instant.parse("2026-09-03T12:00:00Z"));
		assertThat(tip.isActive()).isFalse();
		verify(repository).findByIdAndArchivedAtIsNull(tip.getId());
	}

	@Test
	void listaComEspecificacao() {
		when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
				new FinancialTip("Dica", "Texto", null, LocalDate.of(2026, 9, 3), true)
		)));

		assertThat(service.list("dica", true, LocalDate.of(2026, 9, 3), Pageable.unpaged())).hasSize(1);
		verify(repository).findAll(any(Specification.class), any(Pageable.class));
	}
}

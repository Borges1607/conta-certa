package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.institution.CreateInstitutionRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.repository.InstitutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

	@Mock
	private InstitutionRepository institutionRepository;

	@Test
	void deveNormalizarCnpjAoCriarInstituicao() {
		when(institutionRepository.findByCnpj("11222333000181")).thenReturn(java.util.Optional.empty());
		when(institutionRepository.save(any(Institution.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		InstitutionService service = new InstitutionService(institutionRepository);

		var response = service.create(new CreateInstitutionRequest(
				"Instituto Exemplo",
				"11.222.333/0001-81",
				"contato@example.com",
				"(48) 99999-0000"
		));

		assertThat(response.cnpj()).isEqualTo("11222333000181");
		assertThat(response.contactPhone()).isEqualTo("48999990000");
		assertThat(response.active()).isTrue();
	}

	@Test
	void deveListarOpcoesDeInstituicoesAtivas() {
		when(institutionRepository.findByActiveTrueOrderByNameAsc()).thenReturn(java.util.List.of(
				new Institution("Alfa Escola", "11222333000181", "alfa@example.com", "48999990001", true),
				new Institution("Zeta Escola", "12345678000190", "zeta@example.com", "48999990000", true)
		));
		InstitutionService service = new InstitutionService(institutionRepository);

		var options = service.listActiveOptions();

		assertThat(options)
				.extracting(option -> option.name())
				.containsExactly("Alfa Escola", "Zeta Escola");
	}
}

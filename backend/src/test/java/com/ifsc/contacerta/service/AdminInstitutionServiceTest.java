package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.admin.PatchInstitutionRequest;
import com.ifsc.contacerta.dto.institution.CreateInstitutionRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.repository.AdminHistoryQueryRepository;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInstitutionServiceTest {

	@Mock private InstitutionRepository institutionRepository;
	@Mock private UserRepository userRepository;
	@Mock private AdminHistoryQueryRepository historyQueryRepository;
	@InjectMocks private AdminInstitutionService service;

	@Test
	void criaInstituicaoNormalizandoCnpjEmailETelefone() {
		when(institutionRepository.findByCnpj("12345678000195")).thenReturn(Optional.empty());
		when(institutionRepository.save(any(Institution.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var response = service.create(new CreateInstitutionRequest(
				"  Instituto Alfa ", "12.345.678/0001-95", " CONTATO@EXAMPLE.COM ", "+5548999999999"
		));

		assertThat(response.name()).isEqualTo("Instituto Alfa");
		assertThat(response.cnpj()).isEqualTo("12345678000195");
		assertThat(response.contactEmail()).isEqualTo("contato@example.com");
		assertThat(response.contactPhone()).isEqualTo("+5548999999999");
	}

	@Test
	void rejeitaEdicaoComVersaoObsoleta() {
		Institution institution = new Institution(
				"Instituto Alfa", "12345678000195", "contato@example.com", "+5548999999999", true
		);
		when(institutionRepository.findById(institution.getId())).thenReturn(Optional.of(institution));
		assertThatThrownBy(() -> service.update(institution.getId(), new PatchInstitutionRequest(
				"Novo nome", null, null, null, 1L
		))).isInstanceOfSatisfying(ApiException.class,
				error -> assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT"));
	}

	@Test
	void impedeExclusaoDeInstituicaoComHistorico() {
		Institution institution = new Institution(
				"Instituto Alfa", "12345678000195", "contato@example.com", "+5548999999999", true
		);
		when(institutionRepository.findById(institution.getId())).thenReturn(Optional.of(institution));
		when(historyQueryRepository.hasInstitutionHistory(institution.getId())).thenReturn(true);

		assertThatThrownBy(() -> service.delete(institution.getId()))
				.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.getCode()).isEqualTo("INSTITUTION_HAS_HISTORY"));
		verify(institutionRepository, never()).delete(any(Institution.class));
	}
}

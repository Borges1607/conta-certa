package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.MailOutboxMessage;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.MailOutboxRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountLifecycleControllerTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InstitutionRepository institutionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MailOutboxRepository mailOutboxRepository;

	@Test
	void deveCadastrarAlunoPendenteEConfirmarEmailComToken() throws Exception {
		Institution institution = institutionRepository.saveAndFlush(new Institution(
				"IFSC",
				cnpj(),
				"contato@ifsc.edu.br",
				"48999999999",
				true
		));
		String email = "aluno-" + UUID.randomUUID() + "@example.com";

		mockMvc.perform(post("/auth/student-registration")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "fullName": "Aluno Teste",
							  "email": "%s",
							  "password": "Senha123",
							  "registrationNumber": "20260001",
							  "institutionId": "%s"
							}
							""".formatted(email, institution.getId())))
				.andExpect(status().isAccepted());

		User pendingUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		assertThat(pendingUser.getStatus()).isEqualTo(AccountStatus.PENDING);
		assertThat(pendingUser.getEmailVerifiedAt()).isNull();

		MailOutboxMessage message = mailOutboxRepository.findAll().stream()
				.filter(candidate -> candidate.getRecipient().equals(email))
				.findFirst()
				.orElseThrow();
		String token = tokenFrom(message.getTextBody());

		mockMvc.perform(post("/auth/verify-email")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"token":"%s"}
							""".formatted(token)))
				.andExpect(status().isNoContent());

		User verifiedUser = userRepository.findById(pendingUser.getId()).orElseThrow();
		assertThat(verifiedUser.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(verifiedUser.getEmailVerifiedAt()).isNotNull();
	}

	@Test
	void deveManterRecuperacaoDeSenhaIndistinguivelParaEmailInexistente() throws Exception {
		mockMvc.perform(post("/auth/forgot-password")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"inexistente-%s@example.com"}
							""".formatted(UUID.randomUUID())))
				.andExpect(status().isAccepted());

		assertThat(mailOutboxRepository.count()).isZero();
	}

	private String tokenFrom(String body) {
		URI link = URI.create(body.substring(body.indexOf("http")));
		return link.getQuery().substring("token=".length());
	}

	private String cnpj() {
		return String.format("%014d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000_000_000L);
	}
}

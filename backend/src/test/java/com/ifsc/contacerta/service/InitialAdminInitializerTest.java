package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitialAdminInitializerTest extends PostgresIntegrationTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private PasswordPolicy passwordPolicy;

	@Test
	void deveCriarAdminAtivoComTrocaObrigatoria() {
		String email = uniqueEmail();

		initializer("Admin Inicial", email, "Admin123").run();

		User admin = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(admin.getInstitution()).isNull();
		assertThat(admin.isMustChangePassword()).isTrue();
		assertThat(passwordEncoder.matches("Admin123", admin.getPasswordHash())).isTrue();
	}

	@Test
	void deveNormalizarEmailDoAdminInicial() {
		String email = uniqueEmail();

		initializer("Admin Inicial", "  " + email.toUpperCase() + "  ", "Admin123").run();

		assertThat(userRepository.findByEmailIgnoreCase(email))
				.isPresent()
				.get()
				.extracting(User::getEmail)
				.isEqualTo(email);
	}

	@Test
	void deveSerIdempotenteSemSobrescreverAdminExistente() {
		String email = uniqueEmail();
		InitialAdminInitializer initializer = initializer("Admin Inicial", email, "Admin123");
		initializer.run();
		User first = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		String firstHash = first.getPasswordHash();

		initializer("Outro Nome", email.toUpperCase(), "OutraSenha456").run();

		User persisted = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		assertThat(persisted.getFullName()).isEqualTo("Admin Inicial");
		assertThat(persisted.getPasswordHash()).isEqualTo(firstHash);
	}

	@Test
	void deveIgnorarSeedQuandoTodasAsPropriedadesEstaoAusentes() {
		long usersBefore = userRepository.count();

		initializer("", "", "").run();

		assertThat(userRepository.count()).isEqualTo(usersBefore);
	}

	@Test
	void deveFalharSemExporSenhaQuandoConfiguracaoForParcial() {
		assertThatThrownBy(() -> initializer("Admin Inicial", "", "segredo123").run())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("segredo123");
	}

	private InitialAdminInitializer initializer(String name, String email, String password) {
		return new InitialAdminInitializer(
				new InitialAdminProperties(name, email, password),
				userRepository,
				passwordEncoder,
				passwordPolicy
		);
	}

	private String uniqueEmail() {
		return "admin-" + UUID.randomUUID() + "@contacerta.local";
	}
}

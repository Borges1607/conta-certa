package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.FileDownloadMapper;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.FileDownload;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDownloadServiceTest {

	@Mock private UserRepository userRepository;
	@Mock private FileStorage storage;
	private FileDownloadService service;
	private final UUID fileId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = new FileDownloadService(userRepository, storage, new FileDownloadMapper());
	}

	@Test
	void deveRejeitarUsuarioAusenteAntesDeConsultarArquivo() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());
		assertError(userId, HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
		verifyNoInteractions(storage);
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = {"PENDING", "INACTIVE"})
	void deveRejeitarContaSemAtivacaoAntesDeConsultarArquivo(AccountStatus status) {
		User user = user(Role.STUDENT, status);
		assertError(user.getId(), HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE");
		verifyNoInteractions(storage);
	}

	@Test
	void deveRejeitarAdminAntesDeConsultarArquivo() {
		User user = user(Role.ADMIN, AccountStatus.ACTIVE);
		assertError(user.getId(), HttpStatus.FORBIDDEN, "FILE_ACCESS_FORBIDDEN");
		verifyNoInteractions(storage);
	}

	@Test
	void deveValidarContaAntesDoPapel() {
		User user = user(Role.ADMIN, AccountStatus.INACTIVE);
		assertError(user.getId(), HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE");
		verifyNoInteractions(storage);
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = {"TEACHER", "STUDENT"})
	void deveUsarArquivoAutorizadoParaIdentidadeEPapelSemCompartilharBytesDaEntidade(Role role) {
		User user = user(role, AccountStatus.ACTIVE);
		StoredFile stored = new StoredFile(user, "aula.pdf", "application/pdf", 3, "a".repeat(64), new byte[]{1, 2, 3}, Instant.now());
		if (role == Role.TEACHER) {
			when(storage.findDownloadableByTeacherId(fileId, user.getId())).thenReturn(Optional.of(stored));
		} else {
			when(storage.findDownloadableByStudentId(fileId, user.getId())).thenReturn(Optional.of(stored));
		}
		FileDownload download = service.get(user.getId(), fileId);
		assertThat(download.fileName()).isEqualTo("aula.pdf");
		assertThat(download.contentType()).isEqualTo("application/pdf");
		assertThat(download.sizeBytes()).isEqualTo(3);
		assertThat(download.content()).containsExactly(1, 2, 3);
		download.content()[0] = 9;
		assertThat(stored.getContent()).containsExactly(1, 2, 3);
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = {"TEACHER", "STUDENT"})
	void deveRetornarMesmoErroQuandoArquivoNaoEstaNoEscopo(Role role) {
		User user = user(role, AccountStatus.ACTIVE);
		assertError(user.getId(), HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
	}

	@Test
	void devePropagarFalhaDeArmazenamentoSemRetornarArquivoVazio() {
		User user = user(Role.TEACHER, AccountStatus.ACTIVE);
		IllegalStateException failure = new IllegalStateException("storage unavailable");
		when(storage.findDownloadableByTeacherId(fileId, user.getId())).thenThrow(failure);
		assertThatThrownBy(() -> service.get(user.getId(), fileId)).isSameAs(failure);
	}

	private User user(Role role, AccountStatus status) {
		User user = new User(role, status, "Pessoa", "pessoa@example.com", null, null);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		return user;
	}

	private void assertError(UUID userId, HttpStatus status, String code) {
		assertThatThrownBy(() -> service.get(userId, fileId)).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.getStatus()).isEqualTo(status);
			assertThat(exception.getCode()).isEqualTo(code);
		});
	}
}

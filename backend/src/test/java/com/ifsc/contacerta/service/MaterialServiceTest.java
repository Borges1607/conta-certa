package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.material.CreateMaterialRequest;
import com.ifsc.contacerta.dto.material.PatchMaterialRequest;
import com.ifsc.contacerta.dto.material.TeacherMaterialResponse;
import com.ifsc.contacerta.entity.Material;
import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.MaterialKind;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.MaterialRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	private MaterialService service;
	private FileStorage storage;
	private MaterialRepository materials;
	private User teacher;
	private User anotherTeacher;

	@BeforeEach
	void setUp() {
		UserRepository users = mock(UserRepository.class);
		materials = mock(MaterialRepository.class);
		storage = mock(FileStorage.class);
		service = new MaterialService(
				users, materials, storage, new ExternalUrlValidator(), Clock.fixed(NOW, ZoneOffset.UTC)
		);
		teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", null);
		anotherTeacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Outro", "outro@example.com", "P-2", null);
		when(users.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(materials.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void deveCriarMaterialDeArquivoDoProfessor() {
		StoredFile file = file(teacher);
		when(storage.findByIdAndOwnerTeacherId(file.getId(), teacher.getId())).thenReturn(Optional.of(file));

		TeacherMaterialResponse created = service.create(teacher.getId(), new CreateMaterialRequest(
				"Apostila", null, "Finanças", MaterialKind.FILE, null, file.getId()
		));

		assertThat(created.kind()).isEqualTo(MaterialKind.FILE);
		assertThat(created.file().id()).isEqualTo(file.getId());
		assertThat(created.url()).isNull();
	}

	@Test
	void deveCriarMaterialDeLinkHttps() {
		TeacherMaterialResponse created = service.create(teacher.getId(), new CreateMaterialRequest(
				"Referência", null, null, MaterialKind.EXTERNAL_LINK, "https://example.com/material", null
		));

		assertThat(created.kind()).isEqualTo(MaterialKind.EXTERNAL_LINK);
		assertThat(created.url()).isEqualTo("https://example.com/material");
		assertThat(created.file()).isNull();
	}

	@Test
	void deveRejeitarCombinacaoIncoerenteDeKindEAlvo() {
		assertThatThrownBy(() -> service.create(teacher.getId(), new CreateMaterialRequest(
				"Apostila", null, null, MaterialKind.FILE, "https://example.com", null
		)))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
					assertThat(error.getCode()).isEqualTo("INVALID_MEDIA");
				});
	}

	@Test
	void deveOcultarArquivoDeOutroProfessor() {
		StoredFile file = file(anotherTeacher);
		when(storage.findByIdAndOwnerTeacherId(file.getId(), teacher.getId())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(teacher.getId(), new CreateMaterialRequest(
				"Apostila", null, null, MaterialKind.FILE, null, file.getId()
		)))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(error.getCode()).isEqualTo("FILE_NOT_FOUND");
				});
	}

	@Test
	void deveTrocarMaterialDeArquivoParaLink() throws Exception {
		StoredFile file = file(teacher);
		Material material = Material.file(teacher, "Apostila", null, null, file, NOW);
		when(materials.findByIdAndTeacherId(material.getId(), teacher.getId())).thenReturn(Optional.of(material));

		TeacherMaterialResponse updated = service.update(teacher.getId(), material.getId(), new PatchMaterialRequest(
				0L, null, null, null, MaterialKind.EXTERNAL_LINK,
				new ObjectMapper().readTree("\"https://example.com/referencia\""), NullNode.getInstance()
		));

		assertThat(updated.kind()).isEqualTo(MaterialKind.EXTERNAL_LINK);
		assertThat(updated.url()).isEqualTo("https://example.com/referencia");
		assertThat(updated.file()).isNull();
	}

	@Test
	void deveRejeitarVersaoDivergenteAntesDeAlterar() {
		Material material = Material.externalLink(
				teacher, "Referência", null, null, "https://example.com/old", NOW
		);
		when(materials.findByIdAndTeacherId(material.getId(), teacher.getId())).thenReturn(Optional.of(material));

		assertThatThrownBy(() -> service.update(teacher.getId(), material.getId(), new PatchMaterialRequest(
				4L, "Novo", null, null, null, null, null
		)))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
				});
		assertThat(material.getTitle()).isEqualTo("Referência");
	}

	private StoredFile file(User owner) {
		return new StoredFile(owner, "aula.pdf", "application/pdf", 4, "sha256", new byte[]{1, 2, 3, 4}, NOW);
	}
}

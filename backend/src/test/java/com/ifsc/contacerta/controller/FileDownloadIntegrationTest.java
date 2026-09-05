package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.entity.AuthSession;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Material;
import com.ifsc.contacerta.entity.MediaAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.security.JwtService;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class FileDownloadIntegrationTest extends PostgresIntegrationTest {

	@Autowired private WebApplicationContext context;
	@Autowired private EntityManager entityManager;
	@Autowired private JwtService jwtService;
	@Autowired private ObjectMapper objectMapper;
	private MockMvc mockMvc;
	private User teacher;
	private Institution institution;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		institution = new Institution("Instituto", "11222333000181", "contato@example.com", "48999990000", true);
		entityManager.persist(institution);
		teacher = user(Role.TEACHER, AccountStatus.ACTIVE);
	}

	@Test
	void deveBaixarOsTresFormatosAposUploadSemAlterarConteudoOuVisualizacoes() throws Exception {
		byte[] ppt = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 1};
		assertUploadDownload("aula.pdf", "application/pdf", "%PDF-1.7\nconteúdo".getBytes(StandardCharsets.UTF_8), "inline");
		assertUploadDownload("aula.ppt", "application/vnd.ms-powerpoint", ppt, "attachment");
		assertUploadDownload("aula.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", pptx(), "attachment");
		assertThat(entityManager.createQuery("select count(v) from MediaView v", Long.class).getSingleResult()).isZero();
	}

	@Test
	void devePreservarArquivoNoLimiteDeDezMiB() throws Exception {
		byte[] bytes = new byte[10 * 1024 * 1024];
		Arrays.fill(bytes, (byte) 'a');
		System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 9);
		assertUploadDownload("grande.pdf", "application/pdf", bytes, "inline");
	}

	@Test
	void devePermitirAlunoMatriculadoERevogarDownloadAposRemocao() throws Exception {
		StoredFile file = file("aula.pdf", "application/pdf");
		Material material = Material.file(teacher, "Aula", null, null, file, Instant.now());
		entityManager.persist(material);
		Room room = new Room("Sala", null, Grade.HIGH_SCHOOL_1, List.of(), 50, "ABC234", "hash", teacher, institution);
		entityManager.persist(room);
		entityManager.persist(MediaAssignment.material(room, material, null, 1, Instant.now()));
		User student = user(Role.STUDENT, AccountStatus.ACTIVE);
		RoomMembership membership = new RoomMembership(room, student);
		entityManager.persist(membership);
		String token = token(student);
		mockMvc.perform(get("/files/{id}/download", file.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(content().bytes(file.getContent()));
		membership.remove(teacher);
		entityManager.flush();
		mockMvc.perform(get("/files/{id}/download", file.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
	}

	@Test
	void deveExigirAutenticacaoERecusarAdminEContaInativa() throws Exception {
		UUID fileId = file("aula.pdf", "application/pdf").getId();
		mockMvc.perform(get("/files/{id}/download", fileId)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
		mockMvc.perform(get("/files/{id}/download", fileId).header("Authorization", "Bearer invalid"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
		mockMvc.perform(get("/files/{id}/download", fileId).header("Authorization", "Bearer " + token(user(Role.ADMIN, AccountStatus.ACTIVE))))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FILE_ACCESS_FORBIDDEN"));
		mockMvc.perform(get("/files/{id}/download", fileId).header("Authorization", "Bearer " + token(user(Role.TEACHER, AccountStatus.INACTIVE))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void deveRetornarErroUniformeParaArquivoInexistenteOuDeOutroProfessor() throws Exception {
		String token = token(user(Role.TEACHER, AccountStatus.ACTIVE));
		for (UUID fileId : List.of(file("aula.pdf", "application/pdf").getId(), UUID.randomUUID())) {
			mockMvc.perform(get("/files/{id}/download", fileId).header("Authorization", "Bearer " + token))
					.andExpect(status().isNotFound()).andExpect(content().contentType("application/problem+json"))
					.andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"))
					.andExpect(jsonPath("$.detail").value("File was not found."))
					.andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.traceId").exists());
		}
	}

	@Test
	void deveRejeitarUuidMalformadoComEnvelopeDaApi() throws Exception {
		for (String id : List.of("invalid", "1-1-1-1-1")) {
			mockMvc.perform(get("/files/{id}/download", id).header("Authorization", "Bearer " + token(teacher)))
					.andExpect(status().isBadRequest()).andExpect(content().contentType("application/problem+json"))
					.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
					.andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.traceId").exists());
		}
	}

	@Test
	void deveNormalizarNomesLegadosPreservandoUnicodeEImpedindoInjecaoDeHeaders() throws Exception {
		StoredFile file = file("C:\\pasta/  lição \"A\"\r\n\u0001\u007f.pdf  ", "application/pdf");
		var response = mockMvc.perform(get("/files/{id}/download", file.getId()).header("Authorization", "Bearer " + token(teacher)))
				.andExpect(status().isOk()).andReturn().getResponse();
		assertThat(response.getHeaders("Content-Disposition")).hasSize(1);
		String disposition = response.getHeader("Content-Disposition");
		assertThat(disposition).doesNotContain("\r", "\n", "\u0001", "\u007f");
		assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("lição \"A\".pdf");
	}

	@Test
	void deveUsarNomePadraoQuandoNomeLegadoFicarVazio() throws Exception {
		String[][] formats = {{"application/pdf", "arquivo.pdf"}, {"application/vnd.ms-powerpoint", "arquivo.ppt"},
				{"application/vnd.openxmlformats-officedocument.presentationml.presentation", "arquivo.pptx"}};
		for (String[] format : formats) {
			StoredFile file = file("pasta/ \r\n\u007f ", format[0]);
			var response = mockMvc.perform(get("/files/{id}/download", file.getId()).header("Authorization", "Bearer " + token(teacher)))
					.andExpect(status().isOk()).andReturn().getResponse();
			assertThat(ContentDisposition.parse(response.getHeader("Content-Disposition")).getFilename()).isEqualTo(format[1]);
		}
	}

	private void assertUploadDownload(String name, String mime, byte[] bytes, String disposition) throws Exception {
		String token = token(teacher);
		var upload = mockMvc.perform(multipart("/teacher/materials/files")
				.file(new MockMultipartFile("file", name, mime, bytes)).header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated()).andReturn().getResponse();
		JsonNode json = objectMapper.readTree(upload.getContentAsString());
		UUID fileId = UUID.fromString(json.get("id").asString());
		entityManager.flush();
		entityManager.clear();
		Instant createdAt = entityManager.find(StoredFile.class, fileId).getCreatedAt();
		for (int i = 0; i < 2; i++) {
			var response = mockMvc.perform(get("/files/{id}/download", fileId)
					.header("Authorization", "Bearer " + token).header("Range", "bytes=0-1"))
					.andExpect(status().isOk()).andExpect(content().bytes(bytes))
					.andExpect(header().string("Content-Type", mime))
					.andExpect(header().string("Content-Length", Integer.toString(bytes.length)))
					.andExpect(header().string("Cache-Control", "private, no-store"))
					.andExpect(header().string("X-Content-Type-Options", "nosniff"))
					.andExpect(header().doesNotExist("Accept-Ranges"))
					.andExpect(header().doesNotExist("Content-Range")).andReturn().getResponse();
			ContentDisposition parsed = ContentDisposition.parse(response.getHeader("Content-Disposition"));
			assertThat(parsed.getType()).isEqualTo(disposition);
			assertThat(parsed.getFilename()).isEqualTo(name);
		}
		entityManager.flush();
		entityManager.clear();
		StoredFile persisted = entityManager.find(StoredFile.class, fileId);
		assertThat(persisted.getContent()).isEqualTo(bytes);
		assertThat(persisted.getCreatedAt()).isEqualTo(createdAt);
	}

	private User user(Role role, AccountStatus status) {
		String id = UUID.randomUUID().toString();
		User user = new User(role, status, "Pessoa", id + "@example.com", role == Role.ADMIN ? null : id, role == Role.ADMIN ? null : institution);
		entityManager.persist(user);
		return user;
	}

	private String token(User user) {
		AuthSession session = new AuthSession(user, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now());
		entityManager.persist(session);
		entityManager.flush();
		return jwtService.issue(user.getId(), user.getRole(), session.getId());
	}

	private StoredFile file(String name, String mime) {
		byte[] bytes = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
		StoredFile file = new StoredFile(teacher, name, mime, bytes.length, "a".repeat(64), bytes, Instant.now());
		entityManager.persist(file);
		return file;
	}

	private byte[] pptx() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (String name : List.of("[Content_Types].xml", "ppt/presentation.xml")) {
				zip.putNextEntry(new ZipEntry(name));
				zip.write("<xml/>".getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}
}

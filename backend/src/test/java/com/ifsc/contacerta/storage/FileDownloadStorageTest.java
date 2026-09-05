package com.ifsc.contacerta.storage;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Material;
import com.ifsc.contacerta.entity.MediaAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.service.MaterialFileValidator;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class FileDownloadStorageTest extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

	@Autowired
	private FileStorage storage;
	@Autowired
	private EntityManager entityManager;

	private Institution institution;
	private User teacher;
	private User otherTeacher;
	private User student;

	@BeforeEach
	void setUp() {
		institution = persist(new Institution(
				"Instituto", "11222333000181", "contato@example.com", "48999990000", true
		));
		teacher = persist(user(Role.TEACHER, "Professora", "prof@example.com", "P-1"));
		otherTeacher = persist(user(Role.TEACHER, "Outro professor", "outro@example.com", "P-2"));
		student = persist(user(Role.STUDENT, "Aluno", "aluno@example.com", "A-1"));
	}

	@Test
	void professorPodeBaixarUploadProprioSemMaterial() {
		StoredFile file = file(teacher, "aula.pdf");
		flushAndClear();

		assertThat(storage.findDownloadableByTeacherId(file.getId(), teacher.getId())).isPresent();
		assertThat(storage.findDownloadableByTeacherId(file.getId(), otherTeacher.getId())).isEmpty();
	}

	@Test
	void professorDeOutraInstituicaoNaoPodeBaixarArquivo() {
		Institution otherInstitution = persist(new Institution(
				"Outro instituto", "99888777000166", "outro-instituto@example.com", "48988880000", true
		));
		User externalTeacher = persist(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora externa",
				"externa@example.com", "P-3", otherInstitution
		));
		StoredFile file = file(teacher, "aula.pdf");
		flushAndClear();

		assertThat(storage.findDownloadableByTeacherId(file.getId(), externalTeacher.getId())).isEmpty();
	}

	@Test
	void uuidInexistenteNaoRetornaArquivoParaProfessorOuAluno() {
		UUID missingFileId = UUID.randomUUID();

		assertThat(storage.findDownloadableByTeacherId(missingFileId, teacher.getId())).isEmpty();
		assertThat(storage.findDownloadableByStudentId(missingFileId, student.getId())).isEmpty();
	}

	@Test
	void professorDoMaterialPodeBaixarArquivoMesmoQuandoNaoForDonoDoUpload() {
		StoredFile file = file(otherTeacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		material.archive();
		flushAndClear();

		assertThat(storage.findDownloadableByTeacherId(file.getId(), teacher.getId())).isPresent();
		assertThat(storage.findDownloadableByTeacherId(file.getId(), otherTeacher.getId())).isPresent();
	}

	@Test
	void alunoPrecisaDeMaterialPublicadoAssociadoEMatriculaAtivaNaMesmaSala() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room room = persist(room("Sala", teacher));
		persist(MediaAssignment.material(room, material, null, 1, NOW));
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();

		Room managedRoom = entityManager.find(Room.class, room.getId());
		User managedStudent = entityManager.find(User.class, student.getId());
		persist(new RoomMembership(managedRoom, managedStudent));
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isPresent();
	}

	@Test
	void uploadOrfaoNaoConcedeAcessoAoAluno() {
		StoredFile file = file(teacher, "orfao.pdf");
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();
	}

	@Test
	void alunoNaoPodeBaixarMaterialRascunhoOuArquivado() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room room = persist(room("Sala", teacher));
		persist(MediaAssignment.material(room, material, null, 1, NOW));
		persist(new RoomMembership(room, student));
		entityManager.flush();

		entityManager.createQuery("update Material material set material.status = com.ifsc.contacerta.model.ContentStatus.DRAFT where material.id = :id")
				.setParameter("id", material.getId())
				.executeUpdate();
		entityManager.clear();
		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();

		entityManager.createQuery("update Material material set material.status = com.ifsc.contacerta.model.ContentStatus.ARCHIVED where material.id = :id")
				.setParameter("id", material.getId())
				.executeUpdate();
		entityManager.clear();
		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();
	}

	@Test
	void associacaoComSalaDeOutroProfessorNaoConcedeAcesso() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room inconsistentRoom = persist(room("Sala inconsistente", otherTeacher));
		persist(MediaAssignment.material(inconsistentRoom, material, null, 1, NOW));
		persist(new RoomMembership(inconsistentRoom, student));
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();
	}

	@Test
	void salaArquivadaContinuaElegivel() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room room = persist(room("Sala arquivada", teacher));
		room.archive();
		persist(MediaAssignment.material(room, material, null, 1, NOW));
		persist(new RoomMembership(room, student));
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isPresent();
	}

	@Test
	void umaDeVariasSalasValidasConcedeUmUnicoResultado() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room firstRoom = persist(room("Sala 1", teacher));
		Room secondRoom = persist(room("Sala 2", teacher));
		persist(MediaAssignment.material(firstRoom, material, null, 1, NOW));
		persist(MediaAssignment.material(secondRoom, material, null, 1, NOW));
		persist(new RoomMembership(firstRoom, student));
		persist(new RoomMembership(secondRoom, student));
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isPresent();
	}

	@Test
	void estadoDaAssociacaoOpcionalDeLicaoNaoAlteraAcesso() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room room = persist(room("Sala", teacher));
		Lesson lesson = persist(new Lesson("Lição", null, "Conteúdo", teacher));
		LessonAssignment lessonAssignment = persist(new LessonAssignment(
				room, lesson, 1, null, null, null, null, null, false, false
		));
		persist(MediaAssignment.material(room, material, lessonAssignment, 1, NOW));
		persist(new RoomMembership(room, student));
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isPresent();

		entityManager.find(LessonAssignment.class, lessonAssignment.getId()).archive();
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isPresent();
	}

	@Test
	void matriculaEmSalaDiferenteNaoConcedeAcesso() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room assignedRoom = persist(room("Sala do material", teacher));
		Room membershipRoom = persist(room("Outra sala", teacher));
		persist(MediaAssignment.material(assignedRoom, material, null, 1, NOW));
		persist(new RoomMembership(membershipRoom, student));
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();
	}

	@Test
	void removerMatriculaRevogaAcesso() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room room = persist(room("Sala", teacher));
		persist(MediaAssignment.material(room, material, null, 1, NOW));
		RoomMembership membership = persist(new RoomMembership(room, student));
		flushAndClear();
		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isPresent();

		entityManager.find(RoomMembership.class, membership.getId()).remove(entityManager.find(User.class, teacher.getId()));
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();
	}

	@Test
	void trocarArquivoDoMaterialRevogaAcessoAoArquivoAnterior() {
		StoredFile oldFile = file(teacher, "anterior.pdf");
		StoredFile newFile = file(teacher, "novo.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, oldFile, NOW));
		Room room = persist(room("Sala", teacher));
		persist(MediaAssignment.material(room, material, null, 1, NOW));
		persist(new RoomMembership(room, student));
		flushAndClear();
		assertThat(storage.findDownloadableByStudentId(oldFile.getId(), student.getId())).isPresent();

		entityManager.find(Material.class, material.getId()).updateFile(
				"Material", null, null, entityManager.find(StoredFile.class, newFile.getId())
		);
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(oldFile.getId(), student.getId())).isEmpty();
		assertThat(storage.findDownloadableByStudentId(newFile.getId(), student.getId())).isPresent();
	}

	@Test
	void transformarMaterialEmLinkExternoRevogaAcessoAoArquivo() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room room = persist(room("Sala", teacher));
		persist(MediaAssignment.material(room, material, null, 1, NOW));
		persist(new RoomMembership(room, student));
		flushAndClear();
		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isPresent();

		entityManager.find(Material.class, material.getId()).updateExternalLink(
				"Material", null, null, "https://example.com/material"
		);
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();
	}

	@Test
	void removerUltimaAssociacaoRevogaAcesso() {
		StoredFile file = file(teacher, "material.pdf");
		Material material = persist(Material.file(teacher, "Material", null, null, file, NOW));
		Room room = persist(room("Sala", teacher));
		MediaAssignment assignment = persist(MediaAssignment.material(room, material, null, 1, NOW));
		persist(new RoomMembership(room, student));
		flushAndClear();
		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isPresent();

		entityManager.remove(entityManager.find(MediaAssignment.class, assignment.getId()));
		flushAndClear();

		assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();
	}

	private User user(Role role, String name, String email, String registration) {
		return new User(role, AccountStatus.ACTIVE, name, email, registration, institution);
	}

	private Room room(String name, User owner) {
		return new Room(
				name, null, Grade.HIGH_SCHOOL_1, List.of("Frações"), 70,
				UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
				UUID.randomUUID().toString().replace("-", ""), owner, institution
		);
	}

	private StoredFile file(User owner, String name) {
		return storage.store(
				owner,
				new MaterialFileValidator.ValidatedMaterialFile(name, "application/pdf", new byte[]{1, 2, 3}),
				NOW
		);
	}

	private <T> T persist(T entity) {
		entityManager.persist(entity);
		return entity;
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}

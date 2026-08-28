package com.ifsc.contacerta.entity;

import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.InstitutionRepository;
import com.ifsc.contacerta.repository.MaterialRepository;
import com.ifsc.contacerta.repository.MediaAssignmentRepository;
import com.ifsc.contacerta.repository.MediaViewRepository;
import com.ifsc.contacerta.repository.StoredFileRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.repository.VideoRepository;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class MediaPersistenceTest extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private StoredFileRepository storedFileRepository;
	@Autowired
	private MaterialRepository materialRepository;
	@Autowired
	private VideoRepository videoRepository;
	@Autowired
	private MediaAssignmentRepository mediaAssignmentRepository;
	@Autowired
	private MediaViewRepository mediaViewRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void devePersistirArquivoMaterialVinculoEVisualizacao() {
		Fixture fixture = fixture();
		StoredFile file = storedFileRepository.save(new StoredFile(
				fixture.teacher(), "aula.pdf", "application/pdf", 4, "sha256", new byte[]{1, 2, 3, 4}, NOW
		));
		Material material = materialRepository.save(Material.file(
				fixture.teacher(), "Apostila", null, "Porcentagem", file, NOW
		));
		MediaAssignment assignment = mediaAssignmentRepository.save(MediaAssignment.material(
				fixture.room(), material, fixture.assignment(), 1, NOW
		));
		MediaView view = mediaViewRepository.save(MediaView.material(
				fixture.student(), fixture.room(), material, NOW
		));
		entityManager.flush();

		assertThat(file.getContent()).containsExactly(1, 2, 3, 4);
		assertThat(assignment.getMaterial().getId()).isEqualTo(material.getId());
		assertThat(view.getViewCount()).isEqualTo(1);
		assertThat(view.getFirstViewedAt()).isEqualTo(NOW);
	}

	@Test
	void deveImpedirMesmoVideoDuasVezesNaMesmaSala() {
		Fixture fixture = fixture();
		Video video = videoRepository.save(new Video(
				fixture.teacher(), "Juros", null, "Finanças", "https://example.com/video", NOW
		));
		mediaAssignmentRepository.save(MediaAssignment.video(fixture.room(), video, null, 1, NOW));
		mediaAssignmentRepository.save(MediaAssignment.video(fixture.room(), video, fixture.assignment(), 2, NOW));

		assertThatThrownBy(() -> entityManager.flush())
				.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void deveImpedirVisualizacaoDuplicadaPelaMesmaChaveDeSala() {
		Fixture fixture = fixture();
		Video video = videoRepository.save(new Video(
				fixture.teacher(), "Juros", null, "Finanças", "https://example.com/video", NOW
		));
		mediaViewRepository.save(MediaView.video(fixture.student(), fixture.room(), video, NOW));
		mediaViewRepository.save(MediaView.video(fixture.student(), fixture.room(), video, NOW.plusSeconds(10)));

		assertThatThrownBy(() -> entityManager.flush())
				.isInstanceOf(ConstraintViolationException.class);
	}

	private Fixture fixture() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto", "11222333000181", "contato@example.com", "48999990000", true
		));
		User teacher = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", institution
		));
		User student = userRepository.save(new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno", "aluno@example.com", "A-1", institution
		));
		Room room = new Room(
				"Sala", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 60, "ABC234", "hash", teacher, institution
		);
		Lesson lesson = new Lesson("Porcentagem", null, "# Teoria", teacher);
		entityManager.persist(room);
		entityManager.persist(lesson);
		LessonAssignment assignment = new LessonAssignment(
				room, lesson, 1, null, null, 30, 3, null, false, false
		);
		entityManager.persist(assignment);
		entityManager.flush();
		return new Fixture(teacher, student, room, assignment);
	}

	private record Fixture(User teacher, User student, Room room, LessonAssignment assignment) {}
}

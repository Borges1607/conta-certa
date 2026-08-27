package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class LessonAssignmentRepositoryTest extends PostgresIntegrationTest {

	@Autowired
	private InstitutionRepository institutionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private LessonAssignmentRepository assignmentRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void deveListarAtribuicoesOrdenadasEApenasDoProfessorDaSala() {
		Institution institution = institutionRepository.save(new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		));
		User owner = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		));
		User other = userRepository.save(new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professor Bruno", "bruno@example.com", "PROF-2", institution
		));
		Room room = persistRoom("Sala A", "ABC234", "hash-a", owner, institution);
		Lesson firstLesson = persistLesson("Primeira", owner);
		Lesson secondLesson = persistLesson("Segunda", owner);
		assignmentRepository.save(new LessonAssignment(
				room, secondLesson, 2, null, null, 30, 3, null, true, true
		));
		assignmentRepository.save(new LessonAssignment(
				room, firstLesson, 1, null, null, 30, 3, null, true, true
		));
		assignmentRepository.flush();

		assertThat(assignmentRepository.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(room.getId(), owner.getId()))
				.extracting(assignment -> assignment.getLesson().getTitle())
				.containsExactly("Primeira", "Segunda");
		assertThat(assignmentRepository.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(room.getId(), other.getId()))
				.isEmpty();
		assertThat(assignmentRepository.findByRoomIdForUpdate(room.getId()))
				.extracting(LessonAssignment::getPosition)
				.containsExactly(1, 2);
	}

	private Room persistRoom(String name, String code, String hash, User teacher, Institution institution) {
		Room room = new Room(
				name, null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				code, hash, teacher, institution
		);
		entityManager.persist(room);
		return room;
	}

	private Lesson persistLesson(String title, User teacher) {
		Lesson lesson = new Lesson(title, null, "# Teoria", teacher);
		entityManager.persist(lesson);
		return lesson;
	}
}

package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomMembershipServiceTest {

	@Test
	void deveMatricularAlunoAtivoDaMesmaInstituicao() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana@example.com", "PROF-1", institution);
		User student = user(Role.STUDENT, "bruno@example.com", "ALUNO-1", institution);
		Room room = new Room(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC234", teacher, institution
		);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCode("ABC234")).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.empty());
		when(membershipRepository.save(any(RoomMembership.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		RoomMembershipService service = new RoomMembershipService(
				userRepository, roomRepository, membershipRepository
		);

		var response = service.join(student.getId(), "abc234");

		assertThat(response.roomId()).isEqualTo(room.getId());
		assertThat(response.studentId()).isEqualTo(student.getId());
		assertThat(response.status()).isEqualTo(MembershipStatus.ACTIVE);
	}

	@Test
	void deveRejeitarIngressoDeAlunoDeOutraInstituicao() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		Institution roomInstitution = institution();
		Institution studentInstitution = new Institution(
				"Outro Instituto", "12345678000190", "outro@example.com", "48999990001", true
		);
		User teacher = user(Role.TEACHER, "ana2@example.com", "PROF-2", roomInstitution);
		User student = user(Role.STUDENT, "bruno2@example.com", "ALUNO-2", studentInstitution);
		Room room = new Room(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC235", teacher, roomInstitution
		);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCode("ABC235")).thenReturn(Optional.of(room));
		RoomMembershipService service = new RoomMembershipService(
				userRepository, roomRepository, mock(RoomMembershipRepository.class)
		);

		assertThatThrownBy(() -> service.join(student.getId(), "ABC235"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(403);
					assertThat(exception.getCode()).isEqualTo("INSTITUTION_MISMATCH");
				});
	}

	@Test
	void deveRejeitarIngressoEmSalaArquivada() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana3@example.com", "PROF-3", institution);
		User student = user(Role.STUDENT, "bruno3@example.com", "ALUNO-3", institution);
		Room room = new Room(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC236", teacher, institution
		);
		room.archive();
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCode("ABC236")).thenReturn(Optional.of(room));
		RoomMembershipService service = new RoomMembershipService(
				userRepository, roomRepository, mock(RoomMembershipRepository.class)
		);

		assertThatThrownBy(() -> service.join(student.getId(), "ABC236"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(422);
					assertThat(exception.getCode()).isEqualTo("ROOM_ARCHIVED");
				});
	}

	@Test
	void deveReativarMatriculaRemovidaPreservandoORegistro() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana4@example.com", "PROF-4", institution);
		User student = user(Role.STUDENT, "bruno4@example.com", "ALUNO-4", institution);
		Room room = new Room(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC237", teacher, institution
		);
		RoomMembership membership = new RoomMembership(room, student);
		membership.remove(teacher);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCode("ABC237")).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.of(membership));
		RoomMembershipService service = new RoomMembershipService(
				userRepository, roomRepository, membershipRepository
		);

		var response = service.join(student.getId(), "ABC237");

		assertThat(response.id()).isEqualTo(membership.getId());
		assertThat(response.status()).isEqualTo(MembershipStatus.ACTIVE);
		assertThat(response.removedAt()).isNull();
	}

	@Test
	void devePermitirQueProfessorProprietarioRemovaAluno() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana5@example.com", "PROF-5", institution);
		User student = user(Role.STUDENT, "bruno5@example.com", "ALUNO-5", institution);
		Room room = new Room(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC238", teacher, institution
		);
		RoomMembership membership = new RoomMembership(room, student);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.of(membership));
		RoomMembershipService service = new RoomMembershipService(
				userRepository, roomRepository, membershipRepository
		);

		service.remove(teacher.getId(), room.getId(), student.getId());

		assertThat(membership.getStatus()).isEqualTo(MembershipStatus.REMOVED);
		assertThat(membership.getRemovedBy()).isEqualTo(teacher);
		assertThat(membership.getRemovedAt()).isNotNull();
	}

	private Institution institution() {
		return new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		);
	}

	private User user(Role role, String email, String registrationNumber, Institution institution) {
		return new User(role, AccountStatus.ACTIVE, "Usuário Exemplo", email, registrationNumber, institution);
	}
}

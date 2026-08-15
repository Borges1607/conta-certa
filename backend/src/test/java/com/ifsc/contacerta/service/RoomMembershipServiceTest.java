package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
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

	private Institution institution() {
		return new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		);
	}

	private User user(Role role, String email, String registrationNumber, Institution institution) {
		return new User(role, AccountStatus.ACTIVE, "Usuário Exemplo", email, registrationNumber, institution);
	}
}

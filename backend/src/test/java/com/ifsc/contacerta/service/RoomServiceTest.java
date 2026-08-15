package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.room.CreateRoomRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.Role;
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

class RoomServiceTest {

	@Test
	void deveCriarSalaParaProfessorAtivoComNotaMinimaPadrao() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		JoinCodeGenerator joinCodeGenerator = mock(JoinCodeGenerator.class);
		Institution institution = new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		);
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana@example.com", "PROF-1", institution
		);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(joinCodeGenerator.generateUnique()).thenReturn("ABC234");
		when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
		RoomService service = new RoomService(userRepository, roomRepository, joinCodeGenerator);

		var response = service.create(teacher.getId(), new CreateRoomRequest(
				"1º ano A",
				"Matemática financeira introdutória",
				Grade.HIGH_SCHOOL_1,
				List.of("Porcentagem", "Juros simples"),
				null
		));

		assertThat(response.teacherId()).isEqualTo(teacher.getId());
		assertThat(response.institutionId()).isEqualTo(institution.getId());
		assertThat(response.passingScorePercent()).isEqualTo(50);
		assertThat(response.joinCode()).isEqualTo("ABC234");
	}

	@Test
	void deveRejeitarCriacaoQuandoUsuarioNaoForProfessorAtivo() {
		UserRepository userRepository = mock(UserRepository.class);
		Institution institution = institution();
		User student = new User(
				Role.STUDENT, AccountStatus.ACTIVE, "Aluno Bruno", "bruno@example.com", "ALUNO-1", institution
		);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		RoomService service = new RoomService(
				userRepository, mock(RoomRepository.class), mock(JoinCodeGenerator.class)
		);

		assertThatThrownBy(() -> service.create(student.getId(), validRequest(50)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(403);
					assertThat(exception.getCode()).isEqualTo("TEACHER_REQUIRED");
				});
	}

	@Test
	void deveRejeitarPercentualDeAprovacaoForaDoIntervalo() {
		UserRepository userRepository = mock(UserRepository.class);
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana3@example.com", "PROF-3", institution()
		);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		RoomService service = new RoomService(
				userRepository, mock(RoomRepository.class), mock(JoinCodeGenerator.class)
		);

		assertThatThrownBy(() -> service.create(teacher.getId(), validRequest(101)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(422);
					assertThat(exception.getCode()).isEqualTo("INVALID_PASSING_SCORE");
				});
	}

	@Test
	void deveRejeitarCriacaoPorProfessorInativo() {
		UserRepository userRepository = mock(UserRepository.class);
		User teacher = new User(
				Role.TEACHER, AccountStatus.INACTIVE, "Professora Ana", "ana4@example.com", "PROF-4", institution()
		);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		RoomService service = new RoomService(
				userRepository, mock(RoomRepository.class), mock(JoinCodeGenerator.class)
		);

		assertThatThrownBy(() -> service.create(teacher.getId(), validRequest(50)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(403);
					assertThat(exception.getCode()).isEqualTo("ACCOUNT_INACTIVE");
				});
	}

	@Test
	void deveRejeitarCriacaoEmInstituicaoInativa() {
		UserRepository userRepository = mock(UserRepository.class);
		Institution institution = new Institution(
				"Instituto Inativo", "12345678000190", "contato2@example.com", "48999990001", false
		);
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana5@example.com", "PROF-5", institution
		);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		RoomService service = new RoomService(
				userRepository, mock(RoomRepository.class), mock(JoinCodeGenerator.class)
		);

		assertThatThrownBy(() -> service.create(teacher.getId(), validRequest(50)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(422);
					assertThat(exception.getCode()).isEqualTo("INSTITUTION_INACTIVE");
				});
	}

	private CreateRoomRequest validRequest(Integer passingScore) {
		return new CreateRoomRequest(
				"1º ano A",
				"Matemática financeira introdutória",
				Grade.HIGH_SCHOOL_1,
				List.of("Porcentagem"),
				passingScore
		);
	}

	private Institution institution() {
		return new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		);
	}
}

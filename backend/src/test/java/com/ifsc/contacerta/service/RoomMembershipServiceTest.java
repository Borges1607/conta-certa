package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.room.RoomStudentResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomMembershipServiceTest {

	private final JoinCodeHasher joinCodeHasher = new JoinCodeHasher();

	@Test
	void deveMatricularAlunoAtivoDaMesmaInstituicao() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana@example.com", "PROF-1", institution);
		User student = user(Role.STUDENT, "bruno@example.com", "ALUNO-1", institution);
		Room room = room("ABC234", teacher, institution);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCodeHashAndInstitutionId(
				joinCodeHasher.hash("ABC234"), institution.getId()
		)).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.empty());
		when(membershipRepository.save(any(RoomMembership.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		RoomMembershipService service = service(userRepository, roomRepository, membershipRepository);

		var response = service.join(student.getId(), "abc234");

		assertThat(response.id()).isEqualTo(room.getId());
		assertThat(response.membershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
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
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCodeHashAndInstitutionId(
				joinCodeHasher.hash("ABC235"), studentInstitution.getId()
		)).thenReturn(Optional.empty());
		RoomMembershipService service = service(userRepository, roomRepository, mock(RoomMembershipRepository.class));

		assertThatThrownBy(() -> service.join(student.getId(), "ABC235"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(404);
					assertThat(exception.getCode()).isEqualTo("ROOM_NOT_FOUND");
				});
		verify(roomRepository).findByJoinCodeHashAndInstitutionId(
				joinCodeHasher.hash("ABC235"), studentInstitution.getId()
		);
	}

	@Test
	void deveRejeitarIngressoEmSalaArquivada() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana3@example.com", "PROF-3", institution);
		User student = user(Role.STUDENT, "bruno3@example.com", "ALUNO-3", institution);
		Room room = room("ABC236", teacher, institution);
		room.archive();
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCodeHashAndInstitutionId(
				joinCodeHasher.hash("ABC236"), institution.getId()
		)).thenReturn(Optional.of(room));
		RoomMembershipService service = service(userRepository, roomRepository, mock(RoomMembershipRepository.class));

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
		Room room = room("ABC237", teacher, institution);
		RoomMembership membership = new RoomMembership(room, student);
		UUID membershipId = membership.getId();
		membership.remove(teacher);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCodeHashAndInstitutionId(
				joinCodeHasher.hash("ABC237"), institution.getId()
		)).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.of(membership));
		RoomMembershipService service = service(userRepository, roomRepository, membershipRepository);

		var response = service.join(student.getId(), "ABC237");

		assertThat(membership.getId()).isEqualTo(membershipId);
		assertThat(response.id()).isEqualTo(room.getId());
		assertThat(response.membershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
		assertThat(membership.getRemovedAt()).isNull();
	}

	@Test
	void deveManterMatriculaAtivaAoRepetirIngresso() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana-repetido@example.com", "PROF-7", institution);
		User student = user(Role.STUDENT, "bruno-repetido@example.com", "ALUNO-8", institution);
		Room room = room("ABC240", teacher, institution);
		RoomMembership membership = new RoomMembership(room, student);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCodeHashAndInstitutionId(
				joinCodeHasher.hash("ABC240"), institution.getId()
		)).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.of(membership));
		RoomMembershipService service = service(userRepository, roomRepository, membershipRepository);

		var response = service.join(student.getId(), "abc240");

		assertThat(response.id()).isEqualTo(room.getId());
		assertThat(response.membershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
		assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
	}

	@Test
	void deveListarSomenteSalasComMatriculaAtivaEmOrdemDeIngressoDecrescente() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana-lista@example.com", "PROF-8", institution);
		User student = user(Role.STUDENT, "bruno-lista@example.com", "ALUNO-9", institution);
		Room newerRoom = room("ABC241", teacher, institution);
		Room olderRoom = room("ABC242", teacher, institution);
		RoomMembership newerMembership = new RoomMembership(newerRoom, student);
		RoomMembership olderMembership = new RoomMembership(olderRoom, student);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(membershipRepository.findByStudentIdAndStatusOrderByJoinedAtDesc(student.getId(), MembershipStatus.ACTIVE))
				.thenReturn(List.of(newerMembership, olderMembership));
		RoomMembershipService service = service(userRepository, roomRepository, membershipRepository);

		var responses = service.listStudentRooms(student.getId());

		assertThat(responses).extracting(response -> response.id())
				.containsExactly(newerRoom.getId(), olderRoom.getId());
		assertThat(responses).allSatisfy(response -> {
			assertThat(response.membershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
			assertThat(response.progressPercent()).isZero();
		});
	}

	@Test
	void deveListarAlunosDaSalaDoProfessorComMetricasIniciais() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana-alunos@example.com", "PROF-9", institution);
		User student = user(Role.STUDENT, "bruno-alunos@example.com", "ALUNO-10", institution);
		Room room = room("ABC243", teacher, institution);
		var response = new RoomStudentResponse(
				student.getId(), student.getFullName(), student.getRegistrationNumber(), student.getEmail(),
				0, 0, 0, 0, null, MembershipStatus.ACTIVE
		);
		PageRequest pageable = PageRequest.of(0, 20);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		when(membershipRepository.findStudentResponsesByRoomIdAndStatusAndSearchOrderByJoinedAtDesc(
				room.getId(), MembershipStatus.ACTIVE, "Bruno", pageable
		)).thenReturn(new PageImpl<>(List.of(response), pageable, 1));
		RoomMembershipService service = service(userRepository, roomRepository, membershipRepository);

		var page = service.listRoomStudents(teacher.getId(), room.getId(), "Bruno", pageable);

		assertThat(page.content()).containsExactly(response);
		assertThat(page.content().getFirst().xp()).isZero();
		assertThat(page.content().getFirst().completedLessons()).isZero();
		assertThat(page.content().getFirst().totalLessons()).isZero();
		assertThat(page.content().getFirst().stars()).isZero();
		assertThat(page.content().getFirst().lastActivityAt()).isNull();
	}

	@Test
	void deveOcultarListaDeAlunosQuandoSalaNaoPertencerAoProfessor() {
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana-estrangeira@example.com", "PROF-12", institution);
		UUID teacherId = teacher.getId();
		UUID roomId = UUID.randomUUID();
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
		when(roomRepository.findByIdAndTeacherId(roomId, teacherId)).thenReturn(Optional.empty());
		RoomMembershipService service = service(userRepository, roomRepository, mock(RoomMembershipRepository.class));

		assertThatThrownBy(() -> service.listRoomStudents(teacherId, roomId, null, PageRequest.of(0, 20)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(404);
					assertThat(exception.getCode()).isEqualTo("ROOM_NOT_FOUND");
				});
	}

	@Test
	void devePermitirQueProfessorProprietarioRemovaAluno() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana5@example.com", "PROF-5", institution);
		User student = user(Role.STUDENT, "bruno5@example.com", "ALUNO-5", institution);
		Room room = room("ABC238", teacher, institution);
		RoomMembership membership = new RoomMembership(room, student);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.of(membership));
		RoomMembershipService service = service(userRepository, roomRepository, membershipRepository);

		service.remove(teacher.getId(), room.getId(), student.getId());

		assertThat(membership.getStatus()).isEqualTo(MembershipStatus.REMOVED);
		assertThat(membership.getRemovedBy()).isEqualTo(teacher);
		assertThat(membership.getRemovedAt()).isNotNull();
	}

	@Test
	void deveManterHistoricoAoRepetirRemocao() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana-remocao@example.com", "PROF-10", institution);
		User student = user(Role.STUDENT, "bruno-remocao@example.com", "ALUNO-11", institution);
		Room room = room("ABC244", teacher, institution);
		RoomMembership membership = new RoomMembership(room, student);
		membership.remove(teacher);
		var removedAt = membership.getRemovedAt();
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.of(membership));
		RoomMembershipService service = service(userRepository, roomRepository, membershipRepository);

		service.remove(teacher.getId(), room.getId(), student.getId());

		assertThat(membership.getStatus()).isEqualTo(MembershipStatus.REMOVED);
		assertThat(membership.getRemovedAt()).isEqualTo(removedAt);
		assertThat(membership.getRemovedBy()).isEqualTo(teacher);
	}

	@Test
	void deveRetornarErroExplicitoQuandoMatriculaRemovidaNaoExistir() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana-sem-matricula@example.com", "PROF-11", institution);
		User student = user(Role.STUDENT, "bruno-sem-matricula@example.com", "ALUNO-12", institution);
		Room room = room("ABC245", teacher, institution);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		when(membershipRepository.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.empty());
		RoomMembershipService service = service(userRepository, roomRepository, membershipRepository);

		assertThatThrownBy(() -> service.remove(teacher.getId(), room.getId(), student.getId()))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(404);
					assertThat(exception.getCode()).isEqualTo("MEMBERSHIP_NOT_FOUND");
				});
	}

	@Test
	void deveRejeitarIngressoQuandoUsuarioNaoForAluno() {
		UserRepository userRepository = mock(UserRepository.class);
		Institution institution = institution();
		User teacher = user(Role.TEACHER, "ana6@example.com", "PROF-6", institution);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		RoomMembershipService service = service(userRepository, mock(RoomRepository.class), mock(RoomMembershipRepository.class));

		assertThatThrownBy(() -> service.join(teacher.getId(), "ABC239"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(403);
					assertThat(exception.getCode()).isEqualTo("STUDENT_REQUIRED");
				});
	}

	@Test
	void deveRejeitarIngressoDeAlunoInativo() {
		UserRepository userRepository = mock(UserRepository.class);
		Institution institution = institution();
		User student = new User(
				Role.STUDENT, AccountStatus.INACTIVE, "Aluno Inativo", "inativo@example.com", "ALUNO-6", institution
		);
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		RoomMembershipService service = service(userRepository, mock(RoomRepository.class), mock(RoomMembershipRepository.class));

		assertThatThrownBy(() -> service.join(student.getId(), "ABC239"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(403);
					assertThat(exception.getCode()).isEqualTo("ACCOUNT_INACTIVE");
				});
	}

	@Test
	void deveRetornarErroExplicitoQuandoAlunoNaoExistir() {
		UUID studentId = UUID.randomUUID();
		UserRepository userRepository = mock(UserRepository.class);
		when(userRepository.findById(studentId)).thenReturn(Optional.empty());
		RoomMembershipService service = service(userRepository, mock(RoomRepository.class), mock(RoomMembershipRepository.class));

		assertThatThrownBy(() -> service.join(studentId, "ABC239"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(404);
					assertThat(exception.getCode()).isEqualTo("STUDENT_NOT_FOUND");
				});
	}

	@Test
	void deveRetornarErroExplicitoQuandoCodigoDaSalaNaoExistir() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		User student = user(Role.STUDENT, "bruno6@example.com", "ALUNO-7", institution());
		when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
		when(roomRepository.findByJoinCodeHashAndInstitutionId(
				joinCodeHasher.hash("INVALIDO"), student.getInstitution().getId()
		)).thenReturn(Optional.empty());
		RoomMembershipService service = service(userRepository, roomRepository, mock(RoomMembershipRepository.class));

		assertThatThrownBy(() -> service.join(student.getId(), "invalido"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(404);
					assertThat(exception.getCode()).isEqualTo("ROOM_NOT_FOUND");
				});
	}

	private Institution institution() {
		return new Institution(
				"Instituto Exemplo", "11222333000181", "contato@example.com", "48999990000", true
		);
	}

	private User user(Role role, String email, String registrationNumber, Institution institution) {
		return new User(role, AccountStatus.ACTIVE, "Usuário Exemplo", email, registrationNumber, institution);
	}

	private Room room(String joinCode, User teacher, Institution institution) {
		return new Room(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50,
				joinCode, joinCodeHasher.hash(joinCode), teacher, institution
		);
	}

	private RoomMembershipService service(
			UserRepository userRepository,
			RoomRepository roomRepository,
			RoomMembershipRepository membershipRepository
	) {
		return new RoomMembershipService(userRepository, roomRepository, membershipRepository, joinCodeHasher);
	}
}

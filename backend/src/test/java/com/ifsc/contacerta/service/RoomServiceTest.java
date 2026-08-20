package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.room.CreateRoomRequest;
import com.ifsc.contacerta.dto.room.DuplicateRoomRequest;
import com.ifsc.contacerta.dto.room.UpdateRoomRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Room;
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
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomServiceTest {

	private final JoinCodeHasher joinCodeHasher = new JoinCodeHasher();

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
		RoomService service = service(userRepository, roomRepository, joinCodeGenerator);

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
		RoomService service = service(userRepository, mock(RoomRepository.class), mock(JoinCodeGenerator.class));

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
		RoomService service = service(userRepository, mock(RoomRepository.class), mock(JoinCodeGenerator.class));

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
		RoomService service = service(userRepository, mock(RoomRepository.class), mock(JoinCodeGenerator.class));

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
		RoomService service = service(userRepository, mock(RoomRepository.class), mock(JoinCodeGenerator.class));

		assertThatThrownBy(() -> service.create(teacher.getId(), validRequest(50)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(422);
					assertThat(exception.getCode()).isEqualTo("INSTITUTION_INACTIVE");
				});
	}

	@Test
	void deveAtualizarSalaDoProfessorProprietario() {
		UserRepository userRepository = mock(UserRepository.class);
		RoomRepository roomRepository = mock(RoomRepository.class);
		Institution institution = institution();
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana6@example.com", "PROF-6", institution
		);
		Room room = room("Sala antiga", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC234", teacher, institution);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		RoomService service = service(userRepository, roomRepository, mock(JoinCodeGenerator.class));

		var response = service.update(teacher.getId(), room.getId(), new UpdateRoomRequest(
				"Sala atualizada",
				"Novo conteúdo",
				Grade.HIGH_SCHOOL_2,
				List.of("Juros simples", "Juros compostos"),
				70,
				room.getVersion()
		));

		assertThat(response.name()).isEqualTo("Sala atualizada");
		assertThat(response.grade()).isEqualTo(Grade.HIGH_SCHOOL_2);
		assertThat(response.contentTopics()).containsExactly("Juros simples", "Juros compostos");
		assertThat(response.passingScorePercent()).isEqualTo(70);
	}

	@Test
	void deveRejeitarAtualizacaoPorOutroProfessor() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		Institution institution = institution();
		User owner = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana7@example.com", "PROF-7", institution
		);
		User anotherTeacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professor Carlos", "carlos@example.com", "PROF-8", institution
		);
		Room room = room("1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC235", owner, institution);
		when(roomRepository.findByIdAndTeacherId(room.getId(), owner.getId())).thenReturn(Optional.of(room));
		RoomService service = service(mock(UserRepository.class), roomRepository, mock(JoinCodeGenerator.class));

		when(roomRepository.findByIdAndTeacherId(room.getId(), anotherTeacher.getId())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(anotherTeacher.getId(), room.getId(), new UpdateRoomRequest(
				"Alteração indevida", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, room.getVersion()
		)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(404);
					assertThat(exception.getCode()).isEqualTo("ROOM_NOT_FOUND");
				});
	}

	@Test
	void deveArquivarSalaDeFormaIdempotenteEBloquearEdicao() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		Institution institution = institution();
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana8@example.com", "PROF-9", institution
		);
		Room room = room("1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC236", teacher, institution);
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		RoomService service = service(mock(UserRepository.class), roomRepository, mock(JoinCodeGenerator.class));

		var archived = service.archive(teacher.getId(), room.getId());
		var firstArchivedAt = room.getArchivedAt();
		var archivedAgain = service.archive(teacher.getId(), room.getId());

		assertThat(archived.archived()).isTrue();
		assertThat(archivedAgain.archived()).isTrue();
		assertThat(firstArchivedAt).isNotNull().isEqualTo(room.getArchivedAt());
		assertThatThrownBy(() -> service.update(teacher.getId(), room.getId(), new UpdateRoomRequest(
				"Alteração bloqueada", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, room.getVersion()
		)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(422);
					assertThat(exception.getCode()).isEqualTo("ROOM_ARCHIVED");
				});
	}

	@Test
	void deveRegenerarCodigoDaSalaDoProfessor() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		JoinCodeGenerator joinCodeGenerator = mock(JoinCodeGenerator.class);
		Institution institution = institution();
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana9@example.com", "PROF-10", institution
		);
		Room room = room("1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC237", teacher, institution);
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		when(joinCodeGenerator.generateUnique()).thenReturn("XYZ789");
		RoomService service = service(mock(UserRepository.class), roomRepository, joinCodeGenerator);

		var response = service.regenerateCode(teacher.getId(), room.getId());

		assertThat(response.joinCode()).isEqualTo("XYZ789");
	}

	@Test
	void deveRejeitarPercentualInvalidoAoAtualizarSala() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		Institution institution = institution();
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana10@example.com", "PROF-11", institution
		);
		Room room = room("1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC238", teacher, institution);
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		RoomService service = service(mock(UserRepository.class), roomRepository, mock(JoinCodeGenerator.class));

		assertThatThrownBy(() -> service.update(teacher.getId(), room.getId(), new UpdateRoomRequest(
				"1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), -1, room.getVersion()
		)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo("INVALID_PASSING_SCORE")
				);
	}

	@Test
	void deveDuplicarConfiguracaoDaSalaComNovoIdECodigo() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		JoinCodeGenerator joinCodeGenerator = mock(JoinCodeGenerator.class);
		Institution institution = institution();
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana11@example.com", "PROF-12", institution
		);
		Room source = room(
				"Sala original",
				"Descrição original",
				Grade.HIGH_SCHOOL_2,
				List.of("Juros simples", "Juros compostos"),
				65,
				"ABC239",
				teacher,
				institution
		);
		when(roomRepository.findByIdAndTeacherId(source.getId(), teacher.getId())).thenReturn(Optional.of(source));
		when(joinCodeGenerator.generateUnique()).thenReturn("XYZ790");
		when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
		RoomService service = service(mock(UserRepository.class), roomRepository, joinCodeGenerator);

		var response = service.duplicate(teacher.getId(), source.getId(), new DuplicateRoomRequest("Cópia da sala"));

		assertThat(response.id()).isNotEqualTo(source.getId());
		assertThat(response.name()).isEqualTo("Cópia da sala");
		assertThat(response.description()).isEqualTo(source.getDescription());
		assertThat(response.grade()).isEqualTo(source.getGrade());
		assertThat(response.contentTopics()).containsExactlyElementsOf(source.getContentTopics());
		assertThat(response.passingScorePercent()).isEqualTo(source.getPassingScorePercent());
		assertThat(response.joinCode()).isEqualTo("XYZ790");
	}

	@Test
	void deveListarSomenteSalasDoProfessorComContagemDeAlunos() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana12@example.com", "PROF-13", institution
		);
		Room room = room("1º ano A", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC240", teacher, institution);
		PageRequest pageRequest = PageRequest.of(0, 10);
		when(roomRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Room>>any(), eq(pageRequest)))
				.thenReturn(new PageImpl<>(List.of(room), pageRequest, 1));
		when(membershipRepository.countByRoomIdAndStatus(eq(room.getId()), eq(MembershipStatus.ACTIVE)))
				.thenReturn(3L);
		RoomService service = service(mock(UserRepository.class), roomRepository, mock(JoinCodeGenerator.class), membershipRepository);

		var response = service.list(teacher.getId(), " ano ", false, pageRequest);

		assertThat(response.content()).singleElement().satisfies(summary -> {
			assertThat(summary.id()).isEqualTo(room.getId());
			assertThat(summary.studentCount()).isEqualTo(3);
		});
	}

	@Test
	void deveMesclarPatchParcialERejeitarVersaoDesatualizadaAntesDaMutacao() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		Institution institution = institution();
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana13@example.com", "PROF-14", institution
		);
		Room room = room("Sala original", "Descrição", Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC241", teacher, institution);
		when(roomRepository.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		RoomService service = service(mock(UserRepository.class), roomRepository, mock(JoinCodeGenerator.class));

		var response = service.update(teacher.getId(), room.getId(), new UpdateRoomRequest(
				"Novo nome", null, null, null, null, room.getVersion()
		));

		assertThat(response.name()).isEqualTo("Novo nome");
		assertThat(response.description()).isEqualTo("Descrição");
		assertThat(response.grade()).isEqualTo(Grade.HIGH_SCHOOL_1);
		assertThat(response.contentTopics()).containsExactly("Porcentagem");
		assertThatThrownBy(() -> service.update(teacher.getId(), room.getId(), new UpdateRoomRequest(
				"Não deve mudar", null, null, null, null, room.getVersion() + 1
		)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(409);
					assertThat(exception.getCode()).isEqualTo("VERSION_CONFLICT");
				});
		assertThat(room.getName()).isEqualTo("Novo nome");
	}

	@Test
	void deveExcluirSalaSemMatriculasENuncaExcluirSalaComHistorico() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		RoomMembershipRepository membershipRepository = mock(RoomMembershipRepository.class);
		Institution institution = institution();
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana14@example.com", "PROF-15", institution
		);
		Room unused = room("Sala vazia", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC242", teacher, institution);
		when(roomRepository.findByIdAndTeacherId(unused.getId(), teacher.getId())).thenReturn(Optional.of(unused));
		when(membershipRepository.countByRoomId(unused.getId())).thenReturn(0L);
		RoomService service = service(mock(UserRepository.class), roomRepository, mock(JoinCodeGenerator.class), membershipRepository);

		service.delete(teacher.getId(), unused.getId());

		verify(roomRepository).delete(unused);
		Room used = room("Sala histórica", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC243", teacher, institution);
		when(roomRepository.findByIdAndTeacherId(used.getId(), teacher.getId())).thenReturn(Optional.of(used));
		when(membershipRepository.countByRoomId(used.getId())).thenReturn(1L);
		assertThatThrownBy(() -> service.delete(teacher.getId(), used.getId()))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(409);
					assertThat(exception.getCode()).isEqualTo("ROOM_HAS_HISTORY");
				});
	}

	@Test
	void deveGerarNomeDeCopiaAutomaticoSemColisaoNoEscopoDoProfessor() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		JoinCodeGenerator joinCodeGenerator = mock(JoinCodeGenerator.class);
		Institution institution = institution();
		User teacher = new User(
				Role.TEACHER, AccountStatus.ACTIVE, "Professora Ana", "ana15@example.com", "PROF-16", institution
		);
		Room source = room("Sala original", null, Grade.HIGH_SCHOOL_1, List.of("Porcentagem"), 50, "ABC244", teacher, institution);
		when(roomRepository.findByIdAndTeacherId(source.getId(), teacher.getId())).thenReturn(Optional.of(source));
		when(roomRepository.existsByTeacherIdAndName(teacher.getId(), "Sala original (cópia)")).thenReturn(true);
		when(roomRepository.existsByTeacherIdAndName(teacher.getId(), "Sala original (cópia 2)")).thenReturn(false);
		when(joinCodeGenerator.generateUnique()).thenReturn("XYZ791");
		when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
		RoomService service = service(mock(UserRepository.class), roomRepository, joinCodeGenerator);

		var response = service.duplicate(teacher.getId(), source.getId(), new DuplicateRoomRequest(null));

		assertThat(response.name()).isEqualTo("Sala original (cópia 2)");
	}

	@Test
	void deveNaoEnumerarDetalheDeSalaDeOutroProfessor() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		UUID roomId = UUID.randomUUID();
		UUID anotherTeacherId = UUID.randomUUID();
		when(roomRepository.findByIdAndTeacherId(roomId, anotherTeacherId)).thenReturn(Optional.empty());
		RoomService service = service(mock(UserRepository.class), roomRepository, mock(JoinCodeGenerator.class));

		assertThatThrownBy(() -> service.get(anotherTeacherId, roomId))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus().value()).isEqualTo(404);
					assertThat(exception.getCode()).isEqualTo("ROOM_NOT_FOUND");
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

	private Room room(
			String name,
			String description,
			Grade grade,
			List<String> contentTopics,
			int passingScorePercent,
			String joinCode,
			User teacher,
			Institution institution
	) {
		return new Room(
				name, description, grade, contentTopics, passingScorePercent,
				joinCode, joinCodeHasher.hash(joinCode), teacher, institution
		);
	}

	private RoomService service(
			UserRepository userRepository,
			RoomRepository roomRepository,
			JoinCodeGenerator joinCodeGenerator
	) {
		return service(userRepository, roomRepository, joinCodeGenerator, mock(RoomMembershipRepository.class));
	}

	private RoomService service(
			UserRepository userRepository,
			RoomRepository roomRepository,
			JoinCodeGenerator joinCodeGenerator,
			RoomMembershipRepository membershipRepository
	) {
		return new RoomService(userRepository, roomRepository, membershipRepository, joinCodeGenerator, joinCodeHasher);
	}
}

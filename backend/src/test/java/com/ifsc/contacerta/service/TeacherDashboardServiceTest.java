package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.teacher.TeacherDashboardResponse;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherDashboardServiceTest {

	@Mock private UserRepository userRepository;
	@Mock private RoomRepository roomRepository;
	@Mock private RoomMembershipRepository membershipRepository;
	@Mock private LessonRepository lessonRepository;
	@Mock private LessonAssignmentRepository assignmentRepository;
	@InjectMocks private TeacherDashboardService service;

	@Test
	void deveRetornarTodasAsContagensZeradasParaProfessorSemDados() {
		User teacher = user(Role.TEACHER, AccountStatus.ACTIVE);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));

		assertThat(service.get(teacher.getId())).isEqualTo(new TeacherDashboardResponse(
				new TeacherDashboardResponse.RoomCounts(0, 0, 0),
				new TeacherDashboardResponse.StudentCounts(0, 0),
				new TeacherDashboardResponse.LessonCounts(0, 0, 0),
				new TeacherDashboardResponse.AssignmentCounts(0, 0)
		));
	}

	@Test
	void deveComporContagensPorEstadoNoEscopoDoProfessor() {
		User teacher = user(Role.TEACHER, AccountStatus.ACTIVE);
		UUID teacherId = teacher.getId();
		when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
		when(roomRepository.countByTeacherId(teacherId)).thenReturn(4L);
		when(roomRepository.countByTeacherIdAndArchivedAtIsNull(teacherId)).thenReturn(3L);
		when(roomRepository.countByTeacherIdAndArchivedAtIsNotNull(teacherId)).thenReturn(1L);
		when(membershipRepository.countDistinctStudentsByTeacherId(teacherId)).thenReturn(2L);
		when(membershipRepository.countByRoomTeacherIdAndStatus(teacherId, MembershipStatus.ACTIVE)).thenReturn(3L);
		when(lessonRepository.countByTeacherId(teacherId)).thenReturn(12L);
		when(lessonRepository.countByTeacherIdAndStatus(teacherId, ContentStatus.PUBLISHED)).thenReturn(8L);
		when(lessonRepository.countByTeacherIdAndStatus(teacherId, ContentStatus.DRAFT)).thenReturn(3L);
		when(assignmentRepository.countByRoomTeacherId(teacherId)).thenReturn(24L);
		when(assignmentRepository.countByRoomTeacherIdAndStatus(teacherId, ContentStatus.PUBLISHED)).thenReturn(20L);

		TeacherDashboardResponse response = service.get(teacherId);

		assertThat(response).isEqualTo(new TeacherDashboardResponse(
				new TeacherDashboardResponse.RoomCounts(4, 3, 1),
				new TeacherDashboardResponse.StudentCounts(2, 3),
				new TeacherDashboardResponse.LessonCounts(12, 8, 3),
				new TeacherDashboardResponse.AssignmentCounts(24, 20)
		));
		assertThat(response.rooms().total()).isEqualTo(response.rooms().active() + response.rooms().archived());
	}

	@Test
	void deveRejeitarProfessorInexistenteAntesDeConsultarContagens() {
		UUID teacherId = UUID.randomUUID();
		when(userRepository.findById(teacherId)).thenReturn(Optional.empty());

		assertRejected(teacherId, HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND");
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = {"STUDENT", "ADMIN"})
	void deveRejeitarPapelIncompativelAntesDeConsultarContagens(Role role) {
		User user = user(role, AccountStatus.ACTIVE);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		assertRejected(user.getId(), HttpStatus.FORBIDDEN, "TEACHER_REQUIRED");
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = {"PENDING", "INACTIVE"})
	void deveRejeitarProfessorSemContaAtivaAntesDeConsultarContagens(AccountStatus status) {
		User teacher = user(Role.TEACHER, status);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));

		assertRejected(teacher.getId(), HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE");
	}

	private void assertRejected(UUID userId, HttpStatus status, String code) {
		assertThatThrownBy(() -> service.get(userId))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(status);
					assertThat(exception.getCode()).isEqualTo(code);
				});
		verifyNoInteractions(roomRepository, membershipRepository, lessonRepository, assignmentRepository);
	}

	private User user(Role role, AccountStatus status) {
		return new User(role, status, "Professor", "teacher@example.com", "REG-1", null);
	}
}

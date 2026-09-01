package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.media.MediaCollectionResponse;
import com.ifsc.contacerta.dto.media.StudentVideoResponse;
import com.ifsc.contacerta.dto.media.StudentMaterialResponse;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.MediaAssignment;
import com.ifsc.contacerta.entity.MediaView;
import com.ifsc.contacerta.entity.Material;
import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.entity.Video;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.MediaViewType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.MediaAssignmentRepository;
import com.ifsc.contacerta.repository.MediaViewRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentMediaServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	private StudentMediaService service;
	private MediaAssignmentRepository assignments;
	private MediaViewRepository views;
	private RoomMembershipRepository memberships;
	private User student;
	private Room room;

	@BeforeEach
	void setUp() {
		UserRepository users = mock(UserRepository.class);
		assignments = mock(MediaAssignmentRepository.class);
		views = mock(MediaViewRepository.class);
		memberships = mock(RoomMembershipRepository.class);
		service = new StudentMediaService(users, memberships, assignments, views, Clock.fixed(NOW, ZoneOffset.UTC));
		Institution institution = new Institution("IFSC", "12345678000199", "ifsc@example.com", "48999999999", true);
		User teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", institution);
		student = new User(Role.STUDENT, AccountStatus.ACTIVE, "Aluno", "aluno@example.com", "A-1", institution);
		room = new Room("Sala", null, Grade.HIGH_SCHOOL_1, List.of("Finanças"), 50,
				"ABC123", "hash", teacher, institution);
		when(users.findById(student.getId())).thenReturn(Optional.of(student));
	}

	@Test
	void deveListarApenasVideosPublicadosComVisualizacaoDaSala() {
		RoomMembership membership = new RoomMembership(room, student);
		when(memberships.findByRoomIdAndStudentId(room.getId(), student.getId())).thenReturn(Optional.of(membership));
		Video viewedVideo = new Video(room.getTeacher(), "Juros", "Descrição", null, "https://example.com/1", NOW);
		Video archivedVideo = new Video(room.getTeacher(), "Antigo", null, null, "https://example.com/2", NOW);
		archivedVideo.archive();
		MediaAssignment first = MediaAssignment.video(room, viewedVideo, null, 1, NOW);
		MediaAssignment second = MediaAssignment.video(room, archivedVideo, null, 2, NOW);
		when(assignments.findAccessibleByRoomIdAndStudentIdOrderByPositionAsc(
				room.getId(), student.getId(), MembershipStatus.ACTIVE
		)).thenReturn(List.of(first, second));
		when(views.findByStudentIdAndRoomIdAndVideoId(student.getId(), room.getId(), viewedVideo.getId()))
				.thenReturn(Optional.of(MediaView.video(student, room, viewedVideo, NOW.minusSeconds(60))));

		MediaCollectionResponse<StudentVideoResponse> result = service.videos(student.getId(), room.getId());

		assertThat(result.items()).singleElement().satisfies(video -> {
			assertThat(video.id()).isEqualTo(viewedVideo.getId());
			assertThat(video.viewed()).isTrue();
			assertThat(video.firstViewedAt()).isEqualTo(NOW.minusSeconds(60));
		});
		assertThat(result.viewedCount()).isEqualTo(1);
		assertThat(result.totalCount()).isEqualTo(1);
	}

	@Test
	void deveOcultarSalaSemMatriculaAtiva() {
		assertThatThrownBy(() -> service.videos(student.getId(), room.getId()))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(error.getCode()).isEqualTo("ROOM_NOT_FOUND");
				});
	}

	@Test
	void deveListarMaterialDeArquivoComMetadados() {
		when(memberships.findByRoomIdAndStudentId(room.getId(), student.getId()))
				.thenReturn(Optional.of(new RoomMembership(room, student)));
		StoredFile file = new StoredFile(room.getTeacher(), "aula.pdf", "application/pdf", 4,
				"sha256", new byte[]{1, 2, 3, 4}, NOW);
		Material material = Material.file(room.getTeacher(), "Apostila", null, null, file, NOW);
		when(assignments.findAccessibleByRoomIdAndStudentIdOrderByPositionAsc(
				room.getId(), student.getId(), MembershipStatus.ACTIVE
		))
				.thenReturn(List.of(MediaAssignment.material(room, material, null, 1, NOW)));
		when(views.findByStudentIdAndRoomIdAndMaterialId(student.getId(), room.getId(), material.getId()))
				.thenReturn(Optional.empty());

		MediaCollectionResponse<StudentMaterialResponse> result = service.materials(student.getId(), room.getId());

		assertThat(result.items()).singleElement().satisfies(item -> {
			assertThat(item.fileId()).isEqualTo(file.getId());
			assertThat(item.fileName()).isEqualTo("aula.pdf");
			assertThat(item.fileSizeBytes()).isEqualTo(4);
			assertThat(item.externalUrl()).isNull();
			assertThat(item.viewed()).isFalse();
		});
	}

	@Test
	void deveRegistrarVisualizacaoEmTodasAsSalasAcessiveis() {
		Video video = new Video(room.getTeacher(), "Juros", null, null, "https://example.com/1", NOW);
		MediaAssignment assignment = MediaAssignment.video(room, video, null, 1, NOW);
		when(assignments.findAccessibleVideoAssignments(video.getId(), student.getId(), MembershipStatus.ACTIVE))
				.thenReturn(List.of(assignment));

		service.registerView(student.getId(), MediaViewType.VIDEO, video.getId());

		verify(views).upsertVideo(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(student.getId()),
				org.mockito.ArgumentMatchers.eq(room.getId()), org.mockito.ArgumentMatchers.eq(video.getId()),
				org.mockito.ArgumentMatchers.eq(NOW)
		);
	}
}

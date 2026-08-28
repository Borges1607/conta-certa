package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.media.CreateMediaAssignmentRequest;
import com.ifsc.contacerta.dto.media.MediaAssignmentResponse;
import com.ifsc.contacerta.dto.media.PatchMediaAssignmentRequest;
import com.ifsc.contacerta.entity.Institution;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.MediaAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.entity.Video;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.MediaViewType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.MaterialRepository;
import com.ifsc.contacerta.repository.MediaAssignmentRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.repository.VideoRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaAssignmentServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	private MediaAssignmentService service;
	private RoomRepository rooms;
	private VideoRepository videos;
	private LessonAssignmentRepository lessonAssignments;
	private MediaAssignmentRepository assignments;
	private User teacher;
	private Room room;

	@BeforeEach
	void setUp() {
		UserRepository users = mock(UserRepository.class);
		rooms = mock(RoomRepository.class);
		videos = mock(VideoRepository.class);
		lessonAssignments = mock(LessonAssignmentRepository.class);
		assignments = mock(MediaAssignmentRepository.class);
		service = new MediaAssignmentService(
				users, rooms, videos, mock(MaterialRepository.class), lessonAssignments, assignments,
				Clock.fixed(NOW, ZoneOffset.UTC)
		);
		Institution institution = new Institution("IFSC", "12345678000199", "ifsc@example.com", "48999999999", true);
		teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", institution);
		room = room(teacher, institution, "Sala 1", "ABC123");
		when(users.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(rooms.findByIdAndTeacherId(room.getId(), teacher.getId())).thenReturn(Optional.of(room));
		when(assignments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void devePublicarVideoNoFimDaSalaComLicaoOpcional() {
		Video video = new Video(teacher, "Juros", null, null, "https://example.com/video", NOW);
		LessonAssignment lessonAssignment = lessonAssignment(room, teacher);
		when(videos.findByIdAndTeacherId(video.getId(), teacher.getId())).thenReturn(Optional.of(video));
		when(lessonAssignments.findByIdAndRoomIdAndRoomTeacherId(
				lessonAssignment.getId(), room.getId(), teacher.getId()
		)).thenReturn(Optional.of(lessonAssignment));
		when(assignments.findByRoomIdOrderByPositionAsc(room.getId())).thenReturn(List.of(
				com.ifsc.contacerta.entity.MediaAssignment.video(room, video, null, 3, NOW)
		));

		MediaAssignmentResponse created = service.create(teacher.getId(), room.getId(),
				new CreateMediaAssignmentRequest(MediaViewType.VIDEO, video.getId(), lessonAssignment.getId()));

		assertThat(created.mediaType()).isEqualTo(MediaViewType.VIDEO);
		assertThat(created.mediaId()).isEqualTo(video.getId());
		assertThat(created.lessonAssignmentId()).isEqualTo(lessonAssignment.getId());
		assertThat(created.position()).isEqualTo(4);
	}

	@Test
	void deveOcultarVideoDeOutroProfessor() {
		assertThatThrownBy(() -> service.create(teacher.getId(), room.getId(),
				new CreateMediaAssignmentRequest(MediaViewType.VIDEO, java.util.UUID.randomUUID(), null)))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(error.getCode()).isEqualTo("VIDEO_NOT_FOUND");
				});
	}

	@Test
	void deveOcultarAtribuicaoDeLicaoDeOutraSala() {
		Video video = new Video(teacher, "Juros", null, null, "https://example.com/video", NOW);
		when(videos.findByIdAndTeacherId(video.getId(), teacher.getId())).thenReturn(Optional.of(video));

		assertThatThrownBy(() -> service.create(teacher.getId(), room.getId(),
				new CreateMediaAssignmentRequest(MediaViewType.VIDEO, video.getId(), java.util.UUID.randomUUID())))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(error.getCode()).isEqualTo("LESSON_ASSIGNMENT_NOT_FOUND");
				});
	}

	@Test
	void deveMoverVinculoERenormalizarPosicoes() {
		Video firstVideo = new Video(teacher, "Primeiro", null, null, "https://example.com/1", NOW);
		Video secondVideo = new Video(teacher, "Segundo", null, null, "https://example.com/2", NOW);
		Video thirdVideo = new Video(teacher, "Terceiro", null, null, "https://example.com/3", NOW);
		MediaAssignment first = MediaAssignment.video(room, firstVideo, null, 1, NOW);
		MediaAssignment target = MediaAssignment.video(room, secondVideo, null, 2, NOW);
		MediaAssignment third = MediaAssignment.video(room, thirdVideo, null, 3, NOW);
		when(assignments.findByIdAndRoomIdAndRoomTeacherId(target.getId(), room.getId(), teacher.getId()))
				.thenReturn(Optional.of(target));
		when(assignments.findByRoomIdOrderByPositionAsc(room.getId())).thenReturn(List.of(first, target, third));

		MediaAssignmentResponse updated = service.update(
				teacher.getId(), room.getId(), target.getId(), new PatchMediaAssignmentRequest(0L, null, 1)
		);

		assertThat(updated.position()).isEqualTo(1);
		assertThat(first.getPosition()).isEqualTo(2);
		assertThat(third.getPosition()).isEqualTo(3);
	}

	@Test
	void deveRejeitarVersaoDivergenteAntesDeMover() {
		Video video = new Video(teacher, "Juros", null, null, "https://example.com/video", NOW);
		MediaAssignment assignment = MediaAssignment.video(room, video, null, 1, NOW);
		when(assignments.findByIdAndRoomIdAndRoomTeacherId(assignment.getId(), room.getId(), teacher.getId()))
				.thenReturn(Optional.of(assignment));

		assertThatThrownBy(() -> service.update(
				teacher.getId(), room.getId(), assignment.getId(), new PatchMediaAssignmentRequest(7L, null, 2)
		))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
				});
		assertThat(assignment.getPosition()).isEqualTo(1);
	}

	@Test
	void deveRejeitarVideoJaPublicadoNaSala() {
		Video video = new Video(teacher, "Juros", null, null, "https://example.com/video", NOW);
		when(videos.findByIdAndTeacherId(video.getId(), teacher.getId())).thenReturn(Optional.of(video));
		when(assignments.existsByRoomIdAndVideoId(room.getId(), video.getId())).thenReturn(true);

		assertThatThrownBy(() -> service.create(teacher.getId(), room.getId(),
				new CreateMediaAssignmentRequest(MediaViewType.VIDEO, video.getId(), null)))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(error.getCode()).isEqualTo("MEDIA_ALREADY_ASSIGNED");
				});
	}

	private Room room(User owner, Institution institution, String name, String joinCode) {
		return new Room(name, null, Grade.HIGH_SCHOOL_1, List.of("Finanças"), 50, joinCode, "hash-" + joinCode, owner, institution);
	}

	private LessonAssignment lessonAssignment(Room targetRoom, User owner) {
		return new LessonAssignment(targetRoom, new Lesson("Juros", null, "Teoria", owner), 1,
				null, null, null, null, null, false, false);
	}
}

package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.video.CreateVideoRequest;
import com.ifsc.contacerta.dto.video.PatchVideoRequest;
import com.ifsc.contacerta.dto.video.TeacherVideoResponse;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.entity.Video;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	private UserRepository userRepository;
	private VideoRepository videoRepository;
	private VideoService service;
	private User teacher;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		videoRepository = mock(VideoRepository.class);
		service = new VideoService(
				userRepository,
				videoRepository,
				new ExternalUrlValidator(),
				Clock.fixed(NOW, ZoneOffset.UTC)
		);
		teacher = new User(Role.TEACHER, AccountStatus.ACTIVE, "Professora", "prof@example.com", "P-1", null);
		when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
		when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void deveCriarVideoPublicadoComUrlHttps() {
		TeacherVideoResponse created = service.create(
				teacher.getId(),
				new CreateVideoRequest("Juros", null, "Finanças", "https://example.com/video")
		);

		assertThat(created.title()).isEqualTo("Juros");
		assertThat(created.status()).isEqualTo(ContentStatus.PUBLISHED);
		assertThat(created.createdAt()).isEqualTo(NOW);
		verify(videoRepository).save(any(Video.class));
	}

	@Test
	void deveRejeitarHttpECredenciaisEmUrlExterna() {
		assertInvalidUrl("http://example.com/video");
		assertInvalidUrl("https://user:password@example.com/video");
	}

	@Test
	void deveRejeitarVersaoDivergenteAntesDeAtualizar() {
		Video video = new Video(teacher, "Juros", null, null, "https://example.com/old", NOW);
		when(videoRepository.findByIdAndTeacherId(video.getId(), teacher.getId())).thenReturn(Optional.of(video));

		assertThatThrownBy(() -> service.update(
				teacher.getId(),
				video.getId(),
				new PatchVideoRequest(4L, "Novo", null, null, "https://example.com/new")
		))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
				});
		assertThat(video.getTitle()).isEqualTo("Juros");
	}

	private void assertInvalidUrl(String url) {
		assertThatThrownBy(() -> service.create(
				teacher.getId(), new CreateVideoRequest("Juros", null, null, url)
		))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
					assertThat(error.getCode()).isEqualTo("INVALID_MEDIA");
				});
	}
}

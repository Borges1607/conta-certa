package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.video.CreateVideoRequest;
import com.ifsc.contacerta.dto.video.PatchVideoRequest;
import com.ifsc.contacerta.dto.video.TeacherVideoResponse;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.entity.Video;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoService {

	private final UserRepository userRepository;
	private final VideoRepository videoRepository;
	private final ExternalUrlValidator urlValidator;
	private final Clock clock;

	@Transactional
	public TeacherVideoResponse create(UUID teacherId, CreateVideoRequest request) {
		User teacher = requireActiveTeacher(teacherId);
		String url = urlValidator.requireHttps(request.url(), "video");
		Video video = videoRepository.save(new Video(
				teacher,
				request.title().trim(),
				normalize(request.description()),
				normalize(request.category()),
				url,
				Instant.now(clock)
		));
		return toResponse(video);
	}

	@Transactional
	public TeacherVideoResponse update(UUID teacherId, UUID videoId, PatchVideoRequest request) {
		requireActiveTeacher(teacherId);
		Video video = requireOwnedVideo(teacherId, videoId);
		if (request.version() == null || request.version() != video.getVersion()) {
			throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The video was changed by another request.");
		}
		String title = request.title() == null ? video.getTitle() : request.title().trim();
		String description = resolveNullable(request.description(), video.getDescription());
		String category = resolveNullable(request.category(), video.getCategory());
		String url = request.url() == null ? video.getUrl() : urlValidator.requireHttps(request.url(), "video");
		video.update(title, description, category, url);
		return toResponse(video);
	}

	@Transactional(readOnly = true)
	public PageResponse<TeacherVideoResponse> list(
			UUID teacherId, String search, String category, Pageable pageable
	) {
		requireActiveTeacher(teacherId);
		return PageResponse.from(videoRepository.searchOwned(
				teacherId,
				com.ifsc.contacerta.model.ContentStatus.ARCHIVED,
				normalize(search),
				normalize(category),
				pageable
		).map(this::toResponse));
	}

	@Transactional(readOnly = true)
	public TeacherVideoResponse get(UUID teacherId, UUID videoId) {
		requireActiveTeacher(teacherId);
		return toResponse(requireOwnedVideo(teacherId, videoId));
	}

	@Transactional
	public void archive(UUID teacherId, UUID videoId) {
		requireActiveTeacher(teacherId);
		requireOwnedVideo(teacherId, videoId).archive();
	}

	private User requireActiveTeacher(UUID teacherId) {
		User teacher = userRepository.findById(teacherId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher was not found."));
		if (teacher.getRole() != Role.TEACHER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "A teacher account is required.");
		}
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Teacher account is inactive.");
		}
		return teacher;
	}

	private Video requireOwnedVideo(UUID teacherId, UUID videoId) {
		return videoRepository.findByIdAndTeacherId(videoId, teacherId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND", "Video was not found."));
	}

	private String resolveNullable(JsonNode value, String current) {
		if (value == null) {
			return current;
		}
		return value.isNull() ? null : normalize(value.asText());
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private TeacherVideoResponse toResponse(Video video) {
		return new TeacherVideoResponse(
				video.getId(), video.getTitle(), video.getDescription(), video.getCategory(), video.getUrl(),
				video.getStatus(), video.getCreatedAt(), video.getUpdatedAt(), video.getVersion()
		);
	}
}

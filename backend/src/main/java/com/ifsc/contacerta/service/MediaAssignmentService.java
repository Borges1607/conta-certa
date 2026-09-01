package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.media.CreateMediaAssignmentRequest;
import com.ifsc.contacerta.dto.media.MediaAssignmentResponse;
import com.ifsc.contacerta.dto.media.PatchMediaAssignmentRequest;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Material;
import com.ifsc.contacerta.entity.MediaAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.entity.Video;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MediaViewType;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.MaterialRepository;
import com.ifsc.contacerta.repository.MediaAssignmentRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaAssignmentService {

	private final UserRepository userRepository;
	private final RoomRepository roomRepository;
	private final VideoRepository videoRepository;
	private final MaterialRepository materialRepository;
	private final LessonAssignmentRepository lessonAssignmentRepository;
	private final MediaAssignmentRepository assignmentRepository;
	private final Clock clock;

	@Transactional
	public MediaAssignmentResponse create(UUID teacherId, UUID roomId, CreateMediaAssignmentRequest request) {
		requireActiveTeacher(teacherId);
		Room room = requireOwnedRoom(teacherId, roomId);
		LessonAssignment lesson = resolveLesson(teacherId, roomId, request.lessonAssignmentId());
		int position = assignmentRepository
				.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(roomId, teacherId).stream()
				.map(MediaAssignment::getPosition)
				.max(Comparator.naturalOrder())
				.orElse(0) + 1;
		MediaAssignment assignment;
		if (request.mediaType() == MediaViewType.VIDEO) {
			Video video = videoRepository.findByIdAndTeacherId(request.mediaId(), teacherId)
					.orElseThrow(() -> notFound("VIDEO_NOT_FOUND", "Video was not found."));
			requirePublished(video.getStatus());
			requireNotAssigned(assignmentRepository.existsByRoomIdAndVideoId(roomId, video.getId()));
			assignment = MediaAssignment.video(room, video, lesson, position, Instant.now(clock));
		} else {
			Material material = materialRepository.findByIdAndTeacherId(request.mediaId(), teacherId)
					.orElseThrow(() -> notFound("MATERIAL_NOT_FOUND", "Material was not found."));
			requirePublished(material.getStatus());
			requireNotAssigned(assignmentRepository.existsByRoomIdAndMaterialId(roomId, material.getId()));
			assignment = MediaAssignment.material(room, material, lesson, position, Instant.now(clock));
		}
		return toResponse(assignmentRepository.save(assignment));
	}

	@Transactional
	public MediaAssignmentResponse update(
			UUID teacherId, UUID roomId, UUID assignmentId, PatchMediaAssignmentRequest request
	) {
		requireActiveTeacher(teacherId);
		requireOwnedRoom(teacherId, roomId);
		MediaAssignment target = requireOwnedAssignment(teacherId, roomId, assignmentId);
		if (request.version() == null || request.version() != target.getVersion()) {
			throw new ApiException(
					HttpStatus.CONFLICT, "VERSION_CONFLICT", "The media assignment was changed by another request."
			);
		}
		LessonAssignment lesson = target.getLessonAssignment();
		if (request.lessonAssignmentId() != null) {
			lesson = request.lessonAssignmentId().isNull()
					? null
					: resolveLesson(teacherId, roomId, parseUuid(request.lessonAssignmentId().asText()));
		}
		ArrayList<MediaAssignment> ordered = new ArrayList<>(
				assignmentRepository.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(roomId, teacherId)
		);
		ordered.removeIf(item -> item.getId().equals(target.getId()));
		int requestedPosition = request.position() == null ? target.getPosition() : request.position();
		int targetPosition = Math.max(1, Math.min(requestedPosition, ordered.size() + 1));
		ordered.add(targetPosition - 1, target);
		for (int index = 0; index < ordered.size(); index++) {
			MediaAssignment item = ordered.get(index);
			item.update(item == target ? lesson : item.getLessonAssignment(), index + 1);
		}
		return toResponse(target);
	}

	@Transactional(readOnly = true)
	public List<MediaAssignmentResponse> list(UUID teacherId, UUID roomId) {
		requireActiveTeacher(teacherId);
		requireOwnedRoom(teacherId, roomId);
		return assignmentRepository.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(roomId, teacherId)
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public void delete(UUID teacherId, UUID roomId, UUID assignmentId) {
		requireActiveTeacher(teacherId);
		requireOwnedRoom(teacherId, roomId);
		MediaAssignment target = requireOwnedAssignment(teacherId, roomId, assignmentId);
		assignmentRepository.delete(target);
		ArrayList<MediaAssignment> remaining = new ArrayList<>(
				assignmentRepository.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(roomId, teacherId)
		);
		remaining.removeIf(item -> item.getId().equals(target.getId()));
		for (int index = 0; index < remaining.size(); index++) {
			MediaAssignment item = remaining.get(index);
			item.update(item.getLessonAssignment(), index + 1);
		}
	}

	private User requireActiveTeacher(UUID teacherId) {
		User teacher = userRepository.findById(teacherId)
				.orElseThrow(() -> notFound("TEACHER_NOT_FOUND", "Teacher was not found."));
		if (teacher.getRole() != Role.TEACHER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "A teacher account is required.");
		}
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Teacher account is inactive.");
		}
		return teacher;
	}

	private Room requireOwnedRoom(UUID teacherId, UUID roomId) {
		return roomRepository.findByIdAndTeacherId(roomId, teacherId)
				.orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room was not found."));
	}

	private LessonAssignment resolveLesson(UUID teacherId, UUID roomId, UUID lessonAssignmentId) {
		if (lessonAssignmentId == null) {
			return null;
		}
		return lessonAssignmentRepository.findByIdAndRoomIdAndRoomTeacherId(lessonAssignmentId, roomId, teacherId)
				.orElseThrow(() -> notFound("LESSON_ASSIGNMENT_NOT_FOUND", "Lesson assignment was not found."));
	}

	private MediaAssignment requireOwnedAssignment(UUID teacherId, UUID roomId, UUID assignmentId) {
		return assignmentRepository.findByIdAndRoomIdAndRoomTeacherId(assignmentId, roomId, teacherId)
				.orElseThrow(() -> notFound("MEDIA_ASSIGNMENT_NOT_FOUND", "Media assignment was not found."));
	}

	private UUID parseUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_MEDIA", "Lesson assignment ID is invalid."
			);
		}
	}

	private void requirePublished(ContentStatus status) {
		if (status != ContentStatus.PUBLISHED) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "MEDIA_NOT_PUBLISHED", "Media must be published.");
		}
	}

	private void requireNotAssigned(boolean alreadyAssigned) {
		if (alreadyAssigned) {
			throw new ApiException(
					HttpStatus.CONFLICT, "MEDIA_ALREADY_ASSIGNED", "Media is already assigned to this room."
			);
		}
	}

	private ApiException notFound(String code, String detail) {
		return new ApiException(HttpStatus.NOT_FOUND, code, detail);
	}

	private MediaAssignmentResponse toResponse(MediaAssignment assignment) {
		LessonAssignment lesson = assignment.getLessonAssignment();
		String title = assignment.getMediaType() == MediaViewType.VIDEO
				? assignment.getVideo().getTitle()
				: assignment.getMaterial().getTitle();
		return new MediaAssignmentResponse(
				assignment.getId(), assignment.getRoom().getId(), assignment.getMediaType(), assignment.getMediaId(), title,
				lesson == null ? null : lesson.getId(), lesson == null ? null : lesson.getLesson().getTitle(),
				assignment.getPosition(), assignment.getCreatedAt(), assignment.getVersion()
		);
	}
}

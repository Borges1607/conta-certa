package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.media.MediaCollectionResponse;
import com.ifsc.contacerta.dto.media.MediaLessonLinkResponse;
import com.ifsc.contacerta.dto.media.StudentVideoResponse;
import com.ifsc.contacerta.dto.media.StudentMaterialResponse;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.MediaAssignment;
import com.ifsc.contacerta.entity.MediaView;
import com.ifsc.contacerta.entity.Material;
import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.entity.Video;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MediaViewType;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.MediaAssignmentRepository;
import com.ifsc.contacerta.repository.MediaViewRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentMediaService {

	private final UserRepository userRepository;
	private final RoomMembershipRepository membershipRepository;
	private final MediaAssignmentRepository assignmentRepository;
	private final MediaViewRepository viewRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public MediaCollectionResponse<StudentVideoResponse> videos(UUID studentId, UUID roomId) {
		requireActiveStudent(studentId);
		requireActiveMembership(studentId, roomId);
		List<StudentVideoResponse> items = assignmentRepository.findAccessibleByRoomIdAndStudentIdOrderByPositionAsc(
				roomId, studentId, MembershipStatus.ACTIVE
		).stream()
				.filter(assignment -> assignment.getMediaType() == MediaViewType.VIDEO)
				.filter(assignment -> assignment.getVideo().getStatus() == ContentStatus.PUBLISHED)
				.map(assignment -> toStudentVideo(studentId, roomId, assignment))
				.toList();
		return new MediaCollectionResponse<>(items, items.stream().filter(StudentVideoResponse::viewed).count(), items.size());
	}

	@Transactional(readOnly = true)
	public MediaCollectionResponse<StudentMaterialResponse> materials(UUID studentId, UUID roomId) {
		requireActiveStudent(studentId);
		requireActiveMembership(studentId, roomId);
		List<StudentMaterialResponse> items = assignmentRepository.findAccessibleByRoomIdAndStudentIdOrderByPositionAsc(
				roomId, studentId, MembershipStatus.ACTIVE
		).stream()
				.filter(assignment -> assignment.getMediaType() == MediaViewType.MATERIAL)
				.filter(assignment -> assignment.getMaterial().getStatus() == ContentStatus.PUBLISHED)
				.map(assignment -> toStudentMaterial(studentId, roomId, assignment))
				.toList();
		return new MediaCollectionResponse<>(
				items, items.stream().filter(StudentMaterialResponse::viewed).count(), items.size()
		);
	}

	@Transactional
	public void registerView(UUID studentId, MediaViewType type, UUID mediaId) {
		requireActiveStudent(studentId);
		Instant viewedAt = Instant.now(clock);
		List<MediaAssignment> accessible = type == MediaViewType.VIDEO
				? assignmentRepository.findAccessibleVideoAssignments(mediaId, studentId, MembershipStatus.ACTIVE)
				: assignmentRepository.findAccessibleMaterialAssignments(mediaId, studentId, MembershipStatus.ACTIVE);
		if (accessible.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media was not found.");
		}
		for (MediaAssignment assignment : accessible) {
			if (type == MediaViewType.VIDEO) {
				viewRepository.upsertVideo(UUID.randomUUID(), studentId, assignment.getRoom().getId(), mediaId, viewedAt);
			} else {
				viewRepository.upsertMaterial(UUID.randomUUID(), studentId, assignment.getRoom().getId(), mediaId, viewedAt);
			}
		}
	}

	private StudentVideoResponse toStudentVideo(UUID studentId, UUID roomId, MediaAssignment assignment) {
		Video video = assignment.getVideo();
		MediaView view = viewRepository.findByStudentIdAndRoomIdAndVideoId(studentId, roomId, video.getId()).orElse(null);
		return new StudentVideoResponse(
				video.getId(), video.getTitle(), video.getDescription(), video.getUrl(), null,
				toLesson(assignment.getLessonAssignment()), view != null, view == null ? null : view.getFirstViewedAt()
		);
	}

	private StudentMaterialResponse toStudentMaterial(UUID studentId, UUID roomId, MediaAssignment assignment) {
		Material material = assignment.getMaterial();
		StoredFile file = material.getFile();
		MediaView view = viewRepository.findByStudentIdAndRoomIdAndMaterialId(
				studentId, roomId, material.getId()
		).orElse(null);
		return new StudentMaterialResponse(
				material.getId(), material.getTitle(), material.getDescription(), material.getKind(), material.getExternalUrl(),
				file == null ? null : file.getId(), file == null ? null : file.getFileName(),
				file == null ? null : file.getSizeBytes(), file == null ? null : file.getContentType(),
				toLesson(assignment.getLessonAssignment()), view != null, view == null ? null : view.getFirstViewedAt()
		);
	}

	private MediaLessonLinkResponse toLesson(LessonAssignment assignment) {
		return assignment == null ? null : new MediaLessonLinkResponse(
				assignment.getLesson().getId(), assignment.getLesson().getTitle()
		);
	}

	private User requireActiveStudent(UUID studentId) {
		User student = userRepository.findById(studentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student was not found."));
		if (student.getRole() != Role.STUDENT) {
			throw new ApiException(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "A student account is required.");
		}
		if (student.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Student account is inactive.");
		}
		return student;
	}

	private RoomMembership requireActiveMembership(UUID studentId, UUID roomId) {
		return membershipRepository.findByRoomIdAndStudentId(roomId, studentId)
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room was not found."));
	}
}

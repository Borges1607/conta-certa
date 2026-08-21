package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.room.CreateRoomRequest;
import com.ifsc.contacerta.dto.room.DuplicateRoomRequest;
import com.ifsc.contacerta.dto.room.TeacherRoomDetailResponse;
import com.ifsc.contacerta.dto.room.TeacherRoomSummaryResponse;
import com.ifsc.contacerta.dto.room.UpdateRoomRequest;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.RoomMapper;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import com.ifsc.contacerta.specification.RoomSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

	private static final int DEFAULT_PASSING_SCORE_PERCENT = 50;
	private static final int MAX_ROOM_NAME_LENGTH = 160;

	private final UserRepository userRepository;
	private final RoomRepository roomRepository;
	private final RoomMembershipRepository membershipRepository;
	private final JoinCodeGenerator joinCodeGenerator;
	private final JoinCodeHasher joinCodeHasher;

	@Transactional
	public TeacherRoomDetailResponse create(UUID teacherId, CreateRoomRequest request) {
		User teacher = requireActiveTeacher(teacherId);
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Teacher account is inactive.");
		}
		if (!teacher.getInstitution().isActive()) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INSTITUTION_INACTIVE",
					"The institution is inactive."
			);
		}
		int passingScore = request.passingScorePercent() == null
				? DEFAULT_PASSING_SCORE_PERCENT
				: request.passingScorePercent();
		validatePassingScore(passingScore);
		String joinCode = joinCodeGenerator.generateUnique();
		Room room = new Room(
				request.name(),
				request.description(),
				request.grade(),
				request.contentTopics(),
				passingScore,
				joinCode,
				joinCodeHasher.hash(joinCode),
				teacher,
				teacher.getInstitution()
		);

		Room savedRoom = roomRepository.save(room);
		return toTeacherDetailResponse(savedRoom);
	}

	@Transactional
	public PageResponse<TeacherRoomSummaryResponse> list(
			UUID teacherId,
			String search,
			Boolean archived,
			Pageable pageable
	) {
		requireTeacher(teacherId);
		Page<TeacherRoomSummaryResponse> page = roomRepository
				.findAll(RoomSpecification.ownedBy(teacherId, search, archived), pageable)
				.map(room -> RoomMapper.toTeacherSummaryResponse(
						room,
						membershipRepository.countByRoomIdAndStatus(room.getId(), MembershipStatus.ACTIVE)
				));
		return PageResponse.from(page);
	}

	@Transactional(readOnly = true)
	public TeacherRoomDetailResponse get(UUID teacherId, UUID roomId) {
		requireTeacher(teacherId);
		return toTeacherDetailResponse(requireOwnedRoom(teacherId, roomId));
	}

	@Transactional
	public TeacherRoomDetailResponse update(UUID teacherId, UUID roomId, UpdateRoomRequest request) {
		requireTeacher(teacherId);
		Room room = requireOwnedRoom(teacherId, roomId);
		requireMutable(room);
		requireCurrentVersion(room, request.version());
		String name = request.name() == null ? room.getName() : request.name();
		String description = resolveDescription(room, request.description());
		Grade grade = request.grade() == null ? room.getGrade() : request.grade();
		List<String> contentTopics = request.contentTopics() == null ? room.getContentTopics() : request.contentTopics();
		int passingScore = request.passingScorePercent() == null
				? room.getPassingScorePercent()
				: request.passingScorePercent();
		validateConfiguration(name, grade, contentTopics, passingScore);
		room.update(
				name,
				description,
				grade,
				contentTopics,
				passingScore
		);
		return toTeacherDetailResponse(room);
	}

	@Transactional
	public TeacherRoomDetailResponse archive(UUID teacherId, UUID roomId, Long version) {
		requireTeacher(teacherId);
		Room room = requireOwnedRoom(teacherId, roomId);
		if (room.isArchived()) {
			return toTeacherDetailResponse(room);
		}
		requireCurrentVersion(room, version);
		room.archive();
		return toTeacherDetailResponse(room);
	}

	@Transactional
	public void delete(UUID teacherId, UUID roomId, Long version) {
		requireTeacher(teacherId);
		Room room = requireOwnedRoom(teacherId, roomId);
		requireMutable(room);
		requireCurrentVersion(room, version);
		if (membershipRepository.countByRoomId(roomId) > 0) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"ROOM_HAS_HISTORY",
					"Rooms with membership history cannot be deleted."
			);
		}
		roomRepository.delete(room);
	}

	@Transactional
	public TeacherRoomDetailResponse regenerateCode(UUID teacherId, UUID roomId, Long version) {
		requireTeacher(teacherId);
		Room room = requireOwnedRoom(teacherId, roomId);
		requireMutable(room);
		requireCurrentVersion(room, version);
		String joinCode = joinCodeGenerator.generateUnique();
		room.changeJoinCode(joinCode, joinCodeHasher.hash(joinCode));
		return toTeacherDetailResponse(room);
	}

	@Transactional
	public TeacherRoomDetailResponse duplicate(UUID teacherId, UUID roomId, DuplicateRoomRequest request) {
		requireTeacher(teacherId);
		Room source = requireOwnedRoom(teacherId, roomId);
		requireCurrentVersion(source, request.version());
		String joinCode = joinCodeGenerator.generateUnique();
		Room copy = source.duplicate(resolveDuplicateName(source, request.name()), joinCode, joinCodeHasher.hash(joinCode));
		return toTeacherDetailResponse(roomRepository.save(copy));
	}

	private Room requireOwnedRoom(UUID teacherId, UUID roomId) {
		return roomRepository.findByIdAndTeacherId(roomId, teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND,
				"ROOM_NOT_FOUND",
				"Room was not found."
		));
	}

	private User requireActiveTeacher(UUID teacherId) {
		User teacher = requireTeacher(teacherId);
		if (teacher == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher was not found.");
		}
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Teacher account is inactive.");
		}
		if (!teacher.getInstitution().isActive()) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INSTITUTION_INACTIVE",
					"The institution is inactive."
			);
		}
		return teacher;
	}

	private User requireTeacher(UUID teacherId) {
		return userRepository.findById(teacherId)
				.map(teacher -> {
					if (teacher.getRole() != Role.TEACHER) {
						throw new ApiException(HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "A teacher account is required.");
					}
					return teacher;
				})
				.orElse(null);
	}

	private void requireMutable(Room room) {
		if (room.isArchived()) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"ROOM_ARCHIVED",
					"Archived rooms are read-only."
			);
		}
	}

	private void validatePassingScore(int passingScore) {
		if (passingScore < 0 || passingScore > 100) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_PASSING_SCORE",
					"Passing score must be between 0 and 100."
			);
		}
	}

	private void requireCurrentVersion(Room room, Long requestVersion) {
		if (requestVersion == null || requestVersion != room.getVersion()) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"VERSION_CONFLICT",
					"The room was changed by another request."
			);
		}
	}

	private void validateConfiguration(String name, Grade grade, List<String> contentTopics, int passingScore) {
		if (name == null || name.isBlank() || name.length() > MAX_ROOM_NAME_LENGTH) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ROOM_NAME", "Room name is invalid.");
		}
		if (grade == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "GRADE_REQUIRED", "Grade is required.");
		}
		if (contentTopics == null || contentTopics.isEmpty()
				|| contentTopics.stream().anyMatch(topic -> topic == null || topic.isBlank())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_CONTENT_TOPICS", "Content topics are invalid.");
		}
		validatePassingScore(passingScore);
	}

	private String resolveDescription(Room room, JsonNode description) {
		if (description == null) {
			return room.getDescription();
		}
		if (description.isNull()) {
			return null;
		}
		if (!description.isString()) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_ROOM_DESCRIPTION",
					"Room description must be a string or null."
			);
		}
		return description.stringValue();
	}

	private String resolveDuplicateName(Room source, String requestedName) {
		if (requestedName != null) {
			if (requestedName.isBlank() || requestedName.length() > MAX_ROOM_NAME_LENGTH) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ROOM_NAME", "Room name is invalid.");
			}
			return requestedName;
		}

		int copyNumber = 1;
		String candidate;
		do {
			String suffix = copyNumber == 1 ? " (cópia)" : " (cópia " + copyNumber + ")";
			String sourceName = source.getName();
			candidate = sourceName.substring(0, Math.min(sourceName.length(), MAX_ROOM_NAME_LENGTH - suffix.length())) + suffix;
			copyNumber++;
		} while (roomRepository.existsByTeacherIdAndName(source.getTeacher().getId(), candidate));
		return candidate;
	}

	private TeacherRoomDetailResponse toTeacherDetailResponse(Room room) {
		long studentCount = membershipRepository.countByRoomIdAndStatus(room.getId(), MembershipStatus.ACTIVE);
		long membershipCount = membershipRepository.countByRoomId(room.getId());
		return RoomMapper.toTeacherDetailResponse(room, studentCount, membershipCount);
	}
}

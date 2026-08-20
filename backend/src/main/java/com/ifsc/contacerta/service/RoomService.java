package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.room.CreateRoomRequest;
import com.ifsc.contacerta.dto.room.RoomResponse;
import com.ifsc.contacerta.dto.room.UpdateRoomRequest;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.RoomMapper;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

	private static final int DEFAULT_PASSING_SCORE_PERCENT = 50;

	private final UserRepository userRepository;
	private final RoomRepository roomRepository;
	private final JoinCodeGenerator joinCodeGenerator;
	private final JoinCodeHasher joinCodeHasher;

	@Transactional
	public RoomResponse create(UUID teacherId, CreateRoomRequest request) {
		User teacher = userRepository.findById(teacherId).orElseThrow();
		if (teacher.getRole() != Role.TEACHER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "A teacher account is required.");
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

		return RoomMapper.toResponse(roomRepository.save(room));
	}

	@Transactional
	public RoomResponse update(UUID teacherId, UUID roomId, UpdateRoomRequest request) {
		Room room = requireOwnedRoom(teacherId, roomId);
		requireMutable(room);
		validatePassingScore(request.passingScorePercent());
		room.update(
				request.name(),
				request.description(),
				request.grade(),
				request.contentTopics(),
				request.passingScorePercent()
		);
		return RoomMapper.toResponse(room);
	}

	@Transactional
	public void archive(UUID teacherId, UUID roomId) {
		Room room = requireOwnedRoom(teacherId, roomId);
		room.archive();
	}

	@Transactional
	public RoomResponse regenerateCode(UUID teacherId, UUID roomId) {
		Room room = requireOwnedRoom(teacherId, roomId);
		requireMutable(room);
		String joinCode = joinCodeGenerator.generateUnique();
		room.changeJoinCode(joinCode, joinCodeHasher.hash(joinCode));
		return RoomMapper.toResponse(room);
	}

	@Transactional
	public RoomResponse duplicate(UUID teacherId, UUID roomId, String newName) {
		Room source = requireOwnedRoom(teacherId, roomId);
		String joinCode = joinCodeGenerator.generateUnique();
		Room copy = source.duplicate(newName, joinCode, joinCodeHasher.hash(joinCode));
		return RoomMapper.toResponse(roomRepository.save(copy));
	}

	private Room requireOwnedRoom(UUID teacherId, UUID roomId) {
		Room room = roomRepository.findById(roomId).orElseThrow();
		if (!room.getTeacher().getId().equals(teacherId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ROOM_ACCESS_DENIED", "Room belongs to another teacher.");
		}
		return room;
	}

	private void requireMutable(Room room) {
		if (room.getArchivedAt() != null) {
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
}

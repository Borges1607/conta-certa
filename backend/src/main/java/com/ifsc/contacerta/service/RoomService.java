package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.room.CreateRoomRequest;
import com.ifsc.contacerta.dto.room.RoomResponse;
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
		if (passingScore < 0 || passingScore > 100) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_PASSING_SCORE",
					"Passing score must be between 0 and 100."
			);
		}
		Room room = new Room(
				request.name(),
				request.description(),
				request.grade(),
				request.contentTopics(),
				passingScore,
				joinCodeGenerator.generateUnique(),
				teacher,
				teacher.getInstitution()
		);

		return RoomMapper.toResponse(roomRepository.save(room));
	}
}

package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.room.CreateRoomRequest;
import com.ifsc.contacerta.dto.room.RoomResponse;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.mapper.RoomMapper;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RoomService {

	private static final int DEFAULT_PASSING_SCORE_PERCENT = 50;

	private final UserRepository userRepository;
	private final RoomRepository roomRepository;
	private final JoinCodeGenerator joinCodeGenerator;

	public RoomService(
			UserRepository userRepository,
			RoomRepository roomRepository,
			JoinCodeGenerator joinCodeGenerator
	) {
		this.userRepository = userRepository;
		this.roomRepository = roomRepository;
		this.joinCodeGenerator = joinCodeGenerator;
	}

	@Transactional
	public RoomResponse create(UUID teacherId, CreateRoomRequest request) {
		User teacher = userRepository.findById(teacherId).orElseThrow();
		int passingScore = request.passingScorePercent() == null
				? DEFAULT_PASSING_SCORE_PERCENT
				: request.passingScorePercent();
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

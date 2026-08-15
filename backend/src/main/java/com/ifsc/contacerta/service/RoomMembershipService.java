package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.room.RoomMembershipResponse;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.mapper.RoomMembershipMapper;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomMembershipService {

	private final UserRepository userRepository;
	private final RoomRepository roomRepository;
	private final RoomMembershipRepository membershipRepository;

	@Transactional
	public RoomMembershipResponse join(UUID studentId, String joinCode) {
		User student = userRepository.findById(studentId).orElseThrow();
		Room room = roomRepository.findByJoinCode(joinCode.trim().toUpperCase(Locale.ROOT)).orElseThrow();
		RoomMembership membership = membershipRepository
				.findByRoomIdAndStudentId(room.getId(), studentId)
				.orElseGet(() -> membershipRepository.save(new RoomMembership(room, student)));

		return RoomMembershipMapper.toResponse(membership);
	}
}

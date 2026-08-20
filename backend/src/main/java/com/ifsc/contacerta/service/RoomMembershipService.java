package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.room.RoomMembershipResponse;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.RoomMembershipMapper;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomMembershipService {

	private final UserRepository userRepository;
	private final RoomRepository roomRepository;
	private final RoomMembershipRepository membershipRepository;
	private final JoinCodeHasher joinCodeHasher;

	@Transactional
	public RoomMembershipResponse join(UUID studentId, String joinCode) {
		User student = userRepository.findById(studentId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND,
				"STUDENT_NOT_FOUND",
				"Student was not found."
		));
		if (student.getRole() != Role.STUDENT) {
			throw new ApiException(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "A student account is required.");
		}
		if (student.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Student account is inactive.");
		}
		Room room = roomRepository.findByJoinCodeHash(joinCodeHasher.hash(joinCode)).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room was not found.")
		);
		if (room.getArchivedAt() != null) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"ROOM_ARCHIVED",
					"Archived rooms do not accept new memberships."
			);
		}
		if (!room.getInstitution().getId().equals(student.getInstitution().getId())) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"INSTITUTION_MISMATCH",
					"Student and room must belong to the same institution."
			);
		}
		RoomMembership membership = membershipRepository
				.findByRoomIdAndStudentId(room.getId(), studentId)
				.orElseGet(() -> membershipRepository.save(new RoomMembership(room, student)));
		if (membership.getStatus() == MembershipStatus.REMOVED) {
			membership.reactivate();
		}

		return RoomMembershipMapper.toResponse(membership);
	}

	@Transactional
	public void remove(UUID teacherId, UUID roomId, UUID studentId) {
		User teacher = userRepository.findById(teacherId).orElseThrow();
		Room room = roomRepository.findById(roomId).orElseThrow();
		if (!room.getTeacher().getId().equals(teacherId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ROOM_ACCESS_DENIED", "Room belongs to another teacher.");
		}
		RoomMembership membership = membershipRepository
				.findByRoomIdAndStudentId(roomId, studentId)
				.orElseThrow();
		membership.remove(teacher);
	}
}

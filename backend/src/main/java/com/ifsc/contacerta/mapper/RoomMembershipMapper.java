package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.dto.room.StudentRoomResponse;
import com.ifsc.contacerta.entity.RoomMembership;

public final class RoomMembershipMapper {

	private RoomMembershipMapper() {
	}

	public static StudentRoomResponse toStudentResponse(RoomMembership membership) {
		return RoomMapper.toStudentResponse(membership.getRoom(), membership.getStatus());
	}
}

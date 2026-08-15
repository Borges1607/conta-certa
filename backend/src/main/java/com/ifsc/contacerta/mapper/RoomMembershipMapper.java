package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.dto.room.RoomMembershipResponse;
import com.ifsc.contacerta.entity.RoomMembership;

public final class RoomMembershipMapper {

	private RoomMembershipMapper() {
	}

	public static RoomMembershipResponse toResponse(RoomMembership membership) {
		return new RoomMembershipResponse(
				membership.getId(),
				membership.getRoom().getId(),
				membership.getStudent().getId(),
				membership.getStatus(),
				membership.getJoinedAt(),
				membership.getRemovedAt()
		);
	}
}

package com.ifsc.contacerta.dto.room;

import com.ifsc.contacerta.model.MembershipStatus;

import java.time.Instant;
import java.util.UUID;

public record RoomMembershipResponse(
		UUID id,
		UUID roomId,
		UUID studentId,
		MembershipStatus status,
		Instant joinedAt,
		Instant removedAt
) {
}

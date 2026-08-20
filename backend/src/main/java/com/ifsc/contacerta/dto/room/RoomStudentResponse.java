package com.ifsc.contacerta.dto.room;

import com.ifsc.contacerta.model.MembershipStatus;

import java.time.Instant;
import java.util.UUID;

public record RoomStudentResponse(
		UUID studentId,
		String fullName,
		String registrationNumber,
		String email,
		long xp,
		int completedLessons,
		int totalLessons,
		int stars,
		Instant lastActivityAt,
		MembershipStatus membershipStatus
) {
}

package com.ifsc.contacerta.dto.room;

import com.ifsc.contacerta.dto.institution.InstitutionSummaryResponse;
import com.ifsc.contacerta.model.Grade;
import com.ifsc.contacerta.model.MembershipStatus;

import java.util.List;
import java.util.UUID;

public record StudentRoomResponse(
		UUID id,
		String name,
		String description,
		Grade grade,
		List<String> contentTopics,
		TeacherRoomDetailResponse.TeacherReferenceResponse teacher,
		InstitutionSummaryResponse institution,
		MembershipStatus membershipStatus,
		boolean archived,
		int progressPercent
) {
}

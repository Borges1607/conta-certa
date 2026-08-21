package com.ifsc.contacerta.dto.room;

import com.ifsc.contacerta.dto.institution.InstitutionSummaryResponse;
import com.ifsc.contacerta.model.Grade;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeacherRoomDetailResponse(
		UUID id,
		String name,
		String description,
		Grade grade,
		List<String> contentTopics,
		String joinCode,
		int passingScorePercent,
		boolean archived,
		long studentCount,
		long lessonCount,
		Instant createdAt,
		Instant updatedAt,
		long version,
		InstitutionSummaryResponse institution,
		TeacherReferenceResponse teacher,
		boolean deletable
) {

	public record TeacherReferenceResponse(UUID id, String fullName) {
	}
}

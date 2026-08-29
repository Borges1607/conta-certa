package com.ifsc.contacerta.dto.report;

import java.time.Instant;
import java.util.UUID;

public record TeacherReportRankingResponse(
		int position,
		UUID studentId,
		String fullName,
		String registrationNumber,
		String email,
		long xp,
		long stars,
		Instant firstCompletionAt
) { }

package com.ifsc.contacerta.dto.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TeacherReportStudentResponse(
		UUID studentId,
		String fullName,
		String registrationNumber,
		String email,
		int totalXp,
		int level,
		int totalStars,
		int completedAssignments,
		int passedAssignments,
		Instant lastActivityAt,
		long attemptCount,
		BigDecimal averageScorePercent,
		BigDecimal bestScorePercent
) { }

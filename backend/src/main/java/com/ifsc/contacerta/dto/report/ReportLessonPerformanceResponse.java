package com.ifsc.contacerta.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

public record ReportLessonPerformanceResponse(
		UUID lessonId,
		String lessonTitle,
		long participatingStudentCount,
		long attemptCount,
		BigDecimal averageScorePercent,
		BigDecimal passRatePercent
) { }

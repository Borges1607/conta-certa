package com.ifsc.contacerta.dto.report;

import java.math.BigDecimal;
import java.util.List;

public record TeacherReportOverviewResponse(
		long activeStudentCount,
		long participatingStudentCount,
		BigDecimal averageRoomXp,
		BigDecimal completionRatePercent,
		BigDecimal averageBestStars,
		List<ReportAttemptSeriesItemResponse> attemptSeries,
		ReportScoreDistributionResponse scoreDistribution,
		List<ReportLessonPerformanceResponse> lessonPerformance
) { }

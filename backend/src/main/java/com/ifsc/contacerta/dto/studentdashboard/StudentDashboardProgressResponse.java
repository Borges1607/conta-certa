package com.ifsc.contacerta.dto.studentdashboard;

public record StudentDashboardProgressResponse(
		int totalXp,
		int level,
		int levelProgressPercent,
		int totalStars,
		int completedLessons,
		int passedLessons,
		int totalLessons
) {
}

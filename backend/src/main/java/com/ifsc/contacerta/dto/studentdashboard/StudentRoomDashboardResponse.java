package com.ifsc.contacerta.dto.studentdashboard;

import com.ifsc.contacerta.dto.gamification.AchievementResponse;
import com.ifsc.contacerta.dto.gamification.RankingEntryResponse;
import com.ifsc.contacerta.dto.room.StudentRoomResponse;
import com.ifsc.contacerta.dto.studentlesson.StudentLessonPathResponse;

import java.util.List;

public record StudentRoomDashboardResponse(
		StudentRoomResponse room,
		StudentDashboardProgressResponse progress,
		StudentLessonPathResponse nextLesson,
		List<AchievementResponse> recentAchievements,
		StudentFinancialTipResponse financialTip,
		RankingEntryResponse ranking
) {
}

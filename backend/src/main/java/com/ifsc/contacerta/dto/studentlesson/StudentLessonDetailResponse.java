package com.ifsc.contacerta.dto.studentlesson;

import com.ifsc.contacerta.model.AttemptAvailabilityStatus;
import com.ifsc.contacerta.model.LessonLockReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentLessonDetailResponse(
		UUID assignmentId,
		UUID lessonId,
		UUID roomId,
		String title,
		String summary,
		String theoryMarkdown,
		List<StudentMaterialResponse> materials,
		AttemptAvailabilityStatus availability,
		LessonLockReason lockReason,
		Instant availableFrom,
		Instant dueAt,
		LessonRulesResponse rules,
		Integer bestScorePercent,
		Integer stars,
		UUID activeAttemptId,
		UUID bestAttemptId
) {}

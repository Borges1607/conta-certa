package com.ifsc.contacerta.dto.teacher;

public record TeacherDashboardResponse(
		RoomCounts rooms,
		StudentCounts students,
		LessonCounts lessons,
		AssignmentCounts assignments
) {
	public record RoomCounts(long total, long active, long archived) { }
	public record StudentCounts(long total, long activeMemberships) { }
	public record LessonCounts(long total, long published, long draft) { }
	public record AssignmentCounts(long total, long published) { }
}

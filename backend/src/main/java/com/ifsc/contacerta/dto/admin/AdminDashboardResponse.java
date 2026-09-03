package com.ifsc.contacerta.dto.admin;

public record AdminDashboardResponse(
		InstitutionCounts institutions,
		TeacherCounts teachers
) {
	public record InstitutionCounts(long total, long active, long inactive) {
	}

	public record TeacherCounts(long total, long pending, long active, long inactive) {
	}
}

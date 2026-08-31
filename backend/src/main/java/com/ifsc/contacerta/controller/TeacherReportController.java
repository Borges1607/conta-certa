package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.report.ReportPeriod;
import com.ifsc.contacerta.dto.report.TeacherReportAttemptResponse;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.dto.report.TeacherReportRankingResponse;
import com.ifsc.contacerta.dto.report.TeacherReportStudentResponse;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.TeacherReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/teacher/reports")
@RequiredArgsConstructor
public class TeacherReportController {

	private final TeacherReportService service;

	@GetMapping("/overview")
	public TeacherReportOverviewResponse overview(
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam UUID roomId,
			@RequestParam(required = false) UUID lessonId,
			@RequestParam(required = false) ReportPeriod period,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to
	) {
		return service.overview(currentUser.userId(), roomId, lessonId, period, from, to);
	}

	@GetMapping("/students")
	public PageResponse<TeacherReportStudentResponse> students(
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam UUID roomId,
			@RequestParam(required = false) UUID lessonId,
			@RequestParam(required = false) ReportPeriod period,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "totalXp,desc") String sort
	) {
		SortParts parts = parseSort(sort, "totalXp");
		return PageResponse.from(service.students(
				currentUser.userId(), roomId, lessonId, period, from, to,
				page, size, parts.property(), parts.direction()
		));
	}

	@GetMapping("/students/{studentId}/attempts")
	public PageResponse<TeacherReportAttemptResponse> attempts(
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam UUID roomId,
			@PathVariable UUID studentId,
			@RequestParam(required = false) UUID lessonId,
			@RequestParam(required = false) ReportPeriod period,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "submittedAt,desc") String sort
	) {
		SortParts parts = parseSort(sort, "submittedAt");
		if (!parts.property().equals("submittedAt")) {
			throw badSort();
		}
		return PageResponse.from(service.attempts(
				currentUser.userId(), roomId, studentId, lessonId, period, from, to,
				page, size, parts.direction()
		));
	}

	@GetMapping("/ranking")
	public List<TeacherReportRankingResponse> ranking(
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam UUID roomId,
			@RequestParam(required = false) UUID lessonId,
			@RequestParam(required = false) ReportPeriod period,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to
	) {
		return service.ranking(currentUser.userId(), roomId, lessonId, period, from, to);
	}

	private SortParts parseSort(String value, String defaultProperty) {
		if (value == null || value.isBlank()) {
			return new SortParts(defaultProperty, "desc");
		}
		String[] parts = value.split(",", -1);
		if (parts.length > 2 || parts[0].isBlank() || (parts.length == 2 && parts[1].isBlank())) {
			throw badSort();
		}
		return new SortParts(parts[0], parts.length == 2 ? parts[1] : "desc");
	}

	private ApiException badSort() {
		return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Report sort is malformed.");
	}

	private record SortParts(String property, String direction) { }
}

package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.report.ReportPeriod;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.dto.report.TeacherReportStudentResponse;
import com.ifsc.contacerta.dto.report.TeacherReportAttemptResponse;
import com.ifsc.contacerta.dto.report.TeacherReportRankingResponse;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.ReportFilter;
import com.ifsc.contacerta.model.ReportStudentSort;
import com.ifsc.contacerta.repository.TeacherReportQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherReportService {

	private final TeacherReportFilterFactory filterFactory;
	private final TeacherReportQueryRepository queryRepository;

	@Transactional(readOnly = true)
	public TeacherReportOverviewResponse overview(
			UUID teacherId,
			UUID roomId,
			UUID lessonId,
			ReportPeriod period,
			Instant from,
			Instant to
	) {
		ReportFilter filter = filterFactory.create(teacherId, roomId, lessonId, period, from, to);
		return queryRepository.overview(filter);
	}

	@Transactional(readOnly = true)
	public Page<TeacherReportStudentResponse> students(
			UUID teacherId,
			UUID roomId,
			UUID lessonId,
			ReportPeriod period,
			Instant from,
			Instant to,
			int page,
			int size,
			String sort,
			String direction
	) {
		if (page < 0 || size < 1 || size > 100) {
			throw badRequest("Page must be non-negative and size must be between 1 and 100.");
		}
		try {
			ReportStudentSort.fromProperty(sort);
			Sort.Direction sortDirection = Sort.Direction.fromString(direction);
			ReportFilter filter = filterFactory.create(teacherId, roomId, lessonId, period, from, to);
			return queryRepository.students(filter, PageRequest.of(page, size, Sort.by(sortDirection, sort)));
		} catch (IllegalArgumentException exception) {
			throw badRequest("Unsupported report sort or direction.");
		}
	}

	@Transactional(readOnly = true)
	public Page<TeacherReportAttemptResponse> attempts(
			UUID teacherId,
			UUID roomId,
			UUID studentId,
			UUID lessonId,
			ReportPeriod period,
			Instant from,
			Instant to,
			int page,
			int size,
			String direction
	) {
		if (page < 0 || size < 1 || size > 100) {
			throw badRequest("Page must be non-negative and size must be between 1 and 100.");
		}
		try {
			Sort.Direction sortDirection = Sort.Direction.fromString(direction);
			ReportFilter filter = filterFactory.create(teacherId, roomId, lessonId, period, from, to);
			filterFactory.requireActiveStudent(roomId, studentId);
			PageRequest pageable = PageRequest.of(
					page, size, Sort.by(sortDirection, "submittedAt")
			);
			return queryRepository.attempts(filter, studentId, pageable);
		} catch (IllegalArgumentException exception) {
			throw badRequest("Unsupported report sort direction.");
		}
	}

	@Transactional(readOnly = true)
	public List<TeacherReportRankingResponse> ranking(
			UUID teacherId,
			UUID roomId,
			UUID lessonId,
			ReportPeriod period,
			Instant from,
			Instant to
	) {
		ReportFilter filter = filterFactory.create(teacherId, roomId, lessonId, period, from, to);
		return queryRepository.ranking(filter);
	}

	private ApiException badRequest(String detail) {
		return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", detail);
	}
}

package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.report.ReportPeriod;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.model.ReportFilter;
import com.ifsc.contacerta.repository.TeacherReportQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

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
}

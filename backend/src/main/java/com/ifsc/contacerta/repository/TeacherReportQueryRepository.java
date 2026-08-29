package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.dto.report.TeacherReportAttemptResponse;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.dto.report.TeacherReportRankingResponse;
import com.ifsc.contacerta.dto.report.TeacherReportStudentResponse;
import com.ifsc.contacerta.model.ReportFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.List;

public interface TeacherReportQueryRepository {

	TeacherReportOverviewResponse overview(ReportFilter filter);

	Page<TeacherReportStudentResponse> students(ReportFilter filter, Pageable pageable);

	Page<TeacherReportAttemptResponse> attempts(ReportFilter filter, UUID studentId, Pageable pageable);

	List<TeacherReportRankingResponse> ranking(ReportFilter filter);
}

package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.dto.report.TeacherReportStudentResponse;
import com.ifsc.contacerta.model.ReportFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TeacherReportQueryRepository {

	TeacherReportOverviewResponse overview(ReportFilter filter);

	Page<TeacherReportStudentResponse> students(ReportFilter filter, Pageable pageable);
}

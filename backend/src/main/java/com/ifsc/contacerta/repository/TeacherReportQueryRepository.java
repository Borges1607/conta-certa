package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.model.ReportFilter;

public interface TeacherReportQueryRepository {

	TeacherReportOverviewResponse overview(ReportFilter filter);
}

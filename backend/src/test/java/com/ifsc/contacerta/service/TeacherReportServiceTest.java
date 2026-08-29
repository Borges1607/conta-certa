package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.report.ReportPeriod;
import com.ifsc.contacerta.dto.report.ReportScoreDistributionResponse;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.model.ReportFilter;
import com.ifsc.contacerta.repository.TeacherReportQueryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeacherReportServiceTest {

	@Test
	void deveConsultarOverviewComFiltroValidado() {
		TeacherReportFilterFactory filterFactory = mock(TeacherReportFilterFactory.class);
		TeacherReportQueryRepository queryRepository = mock(TeacherReportQueryRepository.class);
		TeacherReportService service = new TeacherReportService(filterFactory, queryRepository);
		UUID teacherId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		ReportFilter filter = new ReportFilter(roomId, null, null, null);
		TeacherReportOverviewResponse expected = new TeacherReportOverviewResponse(
				0, 0, new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
				List.of(), new ReportScoreDistributionResponse(0, 0, 0, 0), List.of()
		);
		when(filterFactory.create(teacherId, roomId, null, ReportPeriod.ALL, null, null)).thenReturn(filter);
		when(queryRepository.overview(filter)).thenReturn(expected);

		TeacherReportOverviewResponse result = service.overview(
				teacherId, roomId, null, ReportPeriod.ALL, null, null
		);

		assertThat(result).isSameAs(expected);
	}
}

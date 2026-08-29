package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.report.ReportPeriod;
import com.ifsc.contacerta.dto.report.ReportScoreDistributionResponse;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.dto.report.TeacherReportStudentResponse;
import com.ifsc.contacerta.dto.report.TeacherReportAttemptResponse;
import com.ifsc.contacerta.dto.report.TeacherReportRankingResponse;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.ReportFilter;
import com.ifsc.contacerta.repository.TeacherReportQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

	@Test
	void deveConsultarAlunosComPaginacaoEOrdenacaoValidadas() {
		TeacherReportFilterFactory filterFactory = mock(TeacherReportFilterFactory.class);
		TeacherReportQueryRepository queryRepository = mock(TeacherReportQueryRepository.class);
		TeacherReportService service = new TeacherReportService(filterFactory, queryRepository);
		UUID teacherId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		ReportFilter filter = new ReportFilter(roomId, null, null, null);
		PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "totalXp"));
		Page<TeacherReportStudentResponse> expected = new PageImpl<>(List.of(), pageable, 0);
		when(filterFactory.create(teacherId, roomId, null, ReportPeriod.ALL, null, null)).thenReturn(filter);
		when(queryRepository.students(filter, pageable)).thenReturn(expected);

		Page<TeacherReportStudentResponse> result = service.students(
				teacherId, roomId, null, ReportPeriod.ALL, null, null,
				0, 20, "totalXp", "desc"
		);

		assertThat(result).isSameAs(expected);
	}

	@Test
	void deveRejeitarPaginacaoEOrdenacaoInvalidas() {
		TeacherReportService service = new TeacherReportService(
				mock(TeacherReportFilterFactory.class), mock(TeacherReportQueryRepository.class)
		);

		assertBadRequest(() -> service.students(
				UUID.randomUUID(), UUID.randomUUID(), null, null, null, null,
				0, 101, "totalXp", "desc"
		));
		assertBadRequest(() -> service.students(
				UUID.randomUUID(), UUID.randomUUID(), null, null, null, null,
				0, 20, "unknown", "desc"
		));
		assertBadRequest(() -> service.students(
				UUID.randomUUID(), UUID.randomUUID(), null, null, null, null,
				0, 20, "totalXp", "sideways"
		));
	}

	@Test
	void deveConsultarTentativasSomenteAposValidarAlunoAtivo() {
		TeacherReportFilterFactory filterFactory = mock(TeacherReportFilterFactory.class);
		TeacherReportQueryRepository queryRepository = mock(TeacherReportQueryRepository.class);
		TeacherReportService service = new TeacherReportService(filterFactory, queryRepository);
		UUID teacherId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();
		ReportFilter filter = new ReportFilter(roomId, null, null, null);
		PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt"));
		Page<TeacherReportAttemptResponse> expected = new PageImpl<>(List.of(), pageable, 0);
		when(filterFactory.create(teacherId, roomId, null, ReportPeriod.ALL, null, null)).thenReturn(filter);
		when(queryRepository.attempts(filter, studentId, pageable)).thenReturn(expected);

		Page<TeacherReportAttemptResponse> result = service.attempts(
				teacherId, roomId, studentId, null, ReportPeriod.ALL, null, null,
				0, 20, "desc"
		);

		assertThat(result).isSameAs(expected);
	}

	@Test
	void deveConsultarRankingComFiltroValidado() {
		TeacherReportFilterFactory filterFactory = mock(TeacherReportFilterFactory.class);
		TeacherReportQueryRepository queryRepository = mock(TeacherReportQueryRepository.class);
		TeacherReportService service = new TeacherReportService(filterFactory, queryRepository);
		UUID teacherId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		ReportFilter filter = new ReportFilter(roomId, null, null, null);
		List<TeacherReportRankingResponse> expected = List.of();
		when(filterFactory.create(teacherId, roomId, null, ReportPeriod.ALL, null, null)).thenReturn(filter);
		when(queryRepository.ranking(filter)).thenReturn(expected);

		List<TeacherReportRankingResponse> result = service.ranking(
				teacherId, roomId, null, ReportPeriod.ALL, null, null
		);

		assertThat(result).isSameAs(expected);
	}

	private void assertBadRequest(Runnable action) {
		assertThatThrownBy(action::run)
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(exception.getCode()).isEqualTo("BAD_REQUEST");
				});
	}
}

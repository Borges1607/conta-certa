package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.dto.report.ReportAttemptSeriesItemResponse;
import com.ifsc.contacerta.dto.report.ReportLessonPerformanceResponse;
import com.ifsc.contacerta.dto.report.ReportScoreDistributionResponse;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.model.ReportFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcTeacherReportQueryRepository implements TeacherReportQueryRepository {

	private static final BigDecimal ZERO = new BigDecimal("0.00");

	private final JdbcClient jdbcClient;

	@Override
	public TeacherReportOverviewResponse overview(ReportFilter filter) {
		RoomMetrics roomMetrics = roomMetrics(filter);
		CompletionMetrics completionMetrics = completionMetrics(filter);
		return new TeacherReportOverviewResponse(
				roomMetrics.activeStudentCount(),
				participatingStudentCount(filter),
				roomMetrics.averageRoomXp(),
				completionMetrics.completionRatePercent(),
				completionMetrics.averageBestStars(),
				attemptSeries(filter),
				scoreDistribution(filter),
				lessonPerformance(filter)
		);
	}

	private RoomMetrics roomMetrics(ReportFilter filter) {
		return jdbcClient.sql("""
				select count(*) as active_students,
				       coalesce(avg(coalesce(rsp.total_xp, 0)), 0) as average_xp
				from room_memberships rm
				left join room_student_progress rsp
				  on rsp.room_id = rm.room_id and rsp.student_id = rm.student_id
				where rm.room_id = :roomId and rm.status = 'ACTIVE'
				""")
				.param("roomId", filter.roomId())
				.query((rs, rowNum) -> new RoomMetrics(
						rs.getLong("active_students"), decimal(rs.getBigDecimal("average_xp"))
				)).single();
	}

	private CompletionMetrics completionMetrics(ReportFilter filter) {
		String lessonCondition = filter.lessonId() == null ? "" : " and la.lesson_id = :lessonId";
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				with pairs as (
				    select rm.student_id, la.id as assignment_id
				    from room_memberships rm
				    join lesson_assignments la on la.room_id = rm.room_id and la.status = 'PUBLISHED'
				    where rm.room_id = :roomId and rm.status = 'ACTIVE'
				""" + lessonCondition + """
				), results as (
				    select p.student_id, p.assignment_id,
				           bool_or(a.passed is true) as passed,
				           max(a.stars) as best_stars,
				           count(a.id) > 0 as completed
				    from pairs p
				    left join attempts a on a.assignment_id = p.assignment_id
				      and a.student_id = p.student_id and a.status in ('SUBMITTED', 'EXPIRED')
				    group by p.student_id, p.assignment_id
				)
				select case when count(*) = 0 then 0
				            else count(*) filter (where passed) * 100.0 / count(*) end as completion_rate,
				       coalesce(avg(best_stars) filter (where completed), 0) as average_best_stars
				from results
				""").param("roomId", filter.roomId());
		statement = bindLesson(statement, filter);
		return statement.query((rs, rowNum) -> new CompletionMetrics(
				decimal(rs.getBigDecimal("completion_rate")),
				decimal(rs.getBigDecimal("average_best_stars"))
		)).single();
	}

	private long participatingStudentCount(ReportFilter filter) {
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				select count(distinct a.student_id)
				from attempts a
				join lesson_assignments la on la.id = a.assignment_id
				where la.room_id = :roomId
				""" + attemptConditions(filter));
		return bindAttemptFilter(statement.param("roomId", filter.roomId()), filter)
				.query(Long.class).single();
	}

	private List<ReportAttemptSeriesItemResponse> attemptSeries(ReportFilter filter) {
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				select (a.submitted_at at time zone 'UTC')::date as attempt_date, count(*) as attempt_count
				from attempts a
				join lesson_assignments la on la.id = a.assignment_id
				where la.room_id = :roomId
				""" + attemptConditions(filter) + """
				group by attempt_date order by attempt_date
				""");
		return bindAttemptFilter(statement.param("roomId", filter.roomId()), filter)
				.query((rs, rowNum) -> new ReportAttemptSeriesItemResponse(
						rs.getObject("attempt_date", LocalDate.class), rs.getLong("attempt_count")
				)).list();
	}

	private ReportScoreDistributionResponse scoreDistribution(ReportFilter filter) {
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				select count(*) filter (where a.score_percent between 0 and 49) as score_0_49,
				       count(*) filter (where a.score_percent between 50 and 69) as score_50_69,
				       count(*) filter (where a.score_percent between 70 and 89) as score_70_89,
				       count(*) filter (where a.score_percent between 90 and 100) as score_90_100
				from attempts a
				join lesson_assignments la on la.id = a.assignment_id
				where la.room_id = :roomId
				""" + attemptConditions(filter));
		return bindAttemptFilter(statement.param("roomId", filter.roomId()), filter)
				.query((rs, rowNum) -> new ReportScoreDistributionResponse(
						rs.getLong("score_0_49"), rs.getLong("score_50_69"),
						rs.getLong("score_70_89"), rs.getLong("score_90_100")
				)).single();
	}

	private List<ReportLessonPerformanceResponse> lessonPerformance(ReportFilter filter) {
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				select l.id as lesson_id, l.title as lesson_title,
				       count(distinct a.student_id) as participating_students,
				       count(*) as attempt_count,
				       avg(a.score_percent) as average_score,
				       count(*) filter (where a.passed) * 100.0 / count(*) as pass_rate
				from attempts a
				join lesson_assignments la on la.id = a.assignment_id
				join lessons l on l.id = la.lesson_id
				where la.room_id = :roomId
				""" + attemptConditions(filter) + """
				group by l.id, l.title order by l.title, l.id
				""");
		return bindAttemptFilter(statement.param("roomId", filter.roomId()), filter)
				.query((rs, rowNum) -> new ReportLessonPerformanceResponse(
						rs.getObject("lesson_id", UUID.class),
						rs.getString("lesson_title"),
						rs.getLong("participating_students"),
						rs.getLong("attempt_count"),
						decimal(rs.getBigDecimal("average_score")),
						decimal(rs.getBigDecimal("pass_rate"))
				)).list();
	}

	private String attemptConditions(ReportFilter filter) {
		StringBuilder sql = new StringBuilder(" and a.status in ('SUBMITTED', 'EXPIRED')");
		if (filter.lessonId() != null) sql.append(" and la.lesson_id = :lessonId");
		if (!filter.allTime()) sql.append(" and a.submitted_at >= :fromAt and a.submitted_at < :toAt");
		return sql.append('\n').toString();
	}

	private JdbcClient.StatementSpec bindAttemptFilter(JdbcClient.StatementSpec statement, ReportFilter filter) {
		statement = bindLesson(statement, filter);
		if (!filter.allTime()) {
			statement = statement.param("fromAt", Timestamp.from(filter.from()))
					.param("toAt", Timestamp.from(filter.to()));
		}
		return statement;
	}

	private JdbcClient.StatementSpec bindLesson(JdbcClient.StatementSpec statement, ReportFilter filter) {
		return filter.lessonId() == null ? statement : statement.param("lessonId", filter.lessonId());
	}

	private BigDecimal decimal(BigDecimal value) {
		return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
	}

	private record RoomMetrics(long activeStudentCount, BigDecimal averageRoomXp) { }
	private record CompletionMetrics(BigDecimal completionRatePercent, BigDecimal averageBestStars) { }
}

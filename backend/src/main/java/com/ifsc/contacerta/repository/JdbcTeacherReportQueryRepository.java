package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.dto.attempt.AttemptAnswerValueResponse;
import com.ifsc.contacerta.dto.report.ReportAttemptSeriesItemResponse;
import com.ifsc.contacerta.dto.report.ReportLessonPerformanceResponse;
import com.ifsc.contacerta.dto.report.ReportScoreDistributionResponse;
import com.ifsc.contacerta.dto.report.TeacherReportAttemptAnswerResponse;
import com.ifsc.contacerta.dto.report.TeacherReportAttemptResponse;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.dto.report.TeacherReportRankingResponse;
import com.ifsc.contacerta.dto.report.TeacherReportStudentResponse;
import com.ifsc.contacerta.model.AttemptStatus;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.model.ReportFilter;
import com.ifsc.contacerta.model.ReportStudentSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	@Override
	public Page<TeacherReportStudentResponse> students(ReportFilter filter, Pageable pageable) {
		Sort.Order order = pageable.getSort().stream().findFirst()
				.orElseGet(() -> Sort.Order.desc("totalXp"));
		ReportStudentSort sort = ReportStudentSort.fromProperty(order.getProperty());
		String direction = order.isAscending() ? "asc" : "desc";
		String nulls = order.getProperty().equals("lastActivityAt") ? " nulls last" : "";
		String metricsConditions = attemptConditions(filter);
		String sql = """
				select u.id as student_id, u.full_name, u.registration_number, u.email,
				       coalesce(rsp.total_xp, 0) as total_xp,
				       coalesce(rsp.level, 1) as level,
				       coalesce(rsp.total_best_stars, 0) as total_stars,
				       coalesce(rsp.completed_assignment_count, 0) as completed_assignments,
				       coalesce(rsp.passed_assignment_count, 0) as passed_assignments,
				       rsp.last_activity_at,
				       coalesce(metrics.attempt_count, 0) as attempt_count,
				       coalesce(metrics.average_score, 0) as average_score,
				       coalesce(metrics.best_score, 0) as best_score
				from room_memberships rm
				join users u on u.id = rm.student_id
				left join room_student_progress rsp
				  on rsp.room_id = rm.room_id and rsp.student_id = rm.student_id
				left join (
				    select a.student_id, count(*) as attempt_count,
				           avg(a.score_percent) as average_score, max(a.score_percent) as best_score
				    from attempts a
				    join lesson_assignments la on la.id = a.assignment_id
				    where la.room_id = :roomId
				""" + metricsConditions + """
				    group by a.student_id
				) metrics on metrics.student_id = rm.student_id
				where rm.room_id = :roomId and rm.status = 'ACTIVE'
				order by %s %s%s, u.id asc
				limit :limit offset :offset
				""".formatted(sort.expression(), direction, nulls);
		JdbcClient.StatementSpec statement = bindAttemptFilter(
				jdbcClient.sql(sql).param("roomId", filter.roomId()), filter
		).param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());
		List<TeacherReportStudentResponse> content = statement.query((rs, rowNum) ->
				new TeacherReportStudentResponse(
						rs.getObject("student_id", UUID.class),
						rs.getString("full_name"),
						rs.getString("registration_number"),
						rs.getString("email"),
						rs.getInt("total_xp"),
						rs.getInt("level"),
						rs.getInt("total_stars"),
						rs.getInt("completed_assignments"),
						rs.getInt("passed_assignments"),
						toInstant(rs.getObject("last_activity_at", OffsetDateTime.class)),
						rs.getLong("attempt_count"),
						decimal(rs.getBigDecimal("average_score")),
						decimal(rs.getBigDecimal("best_score"))
				)).list();
		long total = jdbcClient.sql("""
				select count(*) from room_memberships
				where room_id = :roomId and status = 'ACTIVE'
				""").param("roomId", filter.roomId()).query(Long.class).single();
		return new PageImpl<>(content, pageable, total);
	}

	@Override
	public Page<TeacherReportAttemptResponse> attempts(
			ReportFilter filter,
			UUID studentId,
			Pageable pageable
	) {
		Sort.Order order = pageable.getSort().stream().findFirst()
				.orElseGet(() -> Sort.Order.desc("submittedAt"));
		String direction = order.isAscending() ? "asc" : "desc";
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				select a.id as attempt_id, l.id as lesson_id, l.title as lesson_title,
				       la.id as assignment_id, a.sequence, a.status, a.started_at, a.submitted_at,
				       extract(epoch from (a.submitted_at - a.started_at))::bigint as duration_seconds,
				       a.total_questions, a.answered_questions, a.correct_answers, a.score_percent,
				       a.passed, a.stars, a.xp_credited
				from attempts a
				join lesson_assignments la on la.id = a.assignment_id
				join lessons l on l.id = la.lesson_id
				where la.room_id = :roomId and a.student_id = :studentId
				""" + attemptConditions(filter) + """
				order by a.submitted_at %s, a.id asc
				limit :limit offset :offset
				""".formatted(direction));
		statement = bindAttemptFilter(
				statement.param("roomId", filter.roomId()).param("studentId", studentId), filter
		).param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());
		List<TeacherReportAttemptResponse> summaries = statement.query((rs, rowNum) ->
				new TeacherReportAttemptResponse(
						rs.getObject("attempt_id", UUID.class),
						rs.getObject("lesson_id", UUID.class),
						rs.getString("lesson_title"),
						rs.getObject("assignment_id", UUID.class),
						rs.getInt("sequence"),
						AttemptStatus.valueOf(rs.getString("status")),
						toInstant(rs.getObject("started_at", OffsetDateTime.class)),
						toInstant(rs.getObject("submitted_at", OffsetDateTime.class)),
						rs.getLong("duration_seconds"),
						rs.getInt("total_questions"),
						rs.getInt("answered_questions"),
						rs.getInt("correct_answers"),
						rs.getInt("score_percent"),
						rs.getBoolean("passed"),
						rs.getInt("stars"),
						rs.getInt("xp_credited"),
						List.of()
				)).list();
		Map<UUID, List<TeacherReportAttemptAnswerResponse>> answers = loadAnswers(
				summaries.stream().map(TeacherReportAttemptResponse::attemptId).toList()
		);
		List<TeacherReportAttemptResponse> content = summaries.stream()
				.map(summary -> withAnswers(summary, answers.getOrDefault(summary.attemptId(), List.of())))
				.toList();
		long total = countAttempts(filter, studentId);
		return new PageImpl<>(content, pageable, total);
	}

	@Override
	public List<TeacherReportRankingResponse> ranking(ReportFilter filter) {
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				with filtered_attempts as (
				    select a.student_id, a.assignment_id, a.xp_credited, a.stars, a.submitted_at
				    from attempts a
				    join lesson_assignments la on la.id = a.assignment_id
				    where la.room_id = :roomId
				""" + attemptConditions(filter) + """
				), xp_totals as (
				    select student_id, coalesce(sum(xp_credited), 0) as xp,
				           min(submitted_at) as first_completion_at
				    from filtered_attempts group by student_id
				), best_stars as (
				    select student_id, assignment_id, max(stars) as stars
				    from filtered_attempts group by student_id, assignment_id
				), star_totals as (
				    select student_id, coalesce(sum(stars), 0) as stars
				    from best_stars group by student_id
				), ranked as (
				    select u.id as student_id, u.full_name, u.registration_number, u.email,
				           coalesce(xp.xp, 0) as xp, coalesce(st.stars, 0) as stars,
				           xp.first_completion_at,
				           row_number() over (order by coalesce(xp.xp, 0) desc,
				             coalesce(st.stars, 0) desc, xp.first_completion_at asc nulls last, u.id asc) as position
				    from room_memberships rm
				    join users u on u.id = rm.student_id
				    left join xp_totals xp on xp.student_id = u.id
				    left join star_totals st on st.student_id = u.id
				    where rm.room_id = :roomId and rm.status = 'ACTIVE'
				)
				select * from ranked order by position
				""");
		return bindAttemptFilter(statement.param("roomId", filter.roomId()), filter)
				.query((rs, rowNum) -> new TeacherReportRankingResponse(
						Math.toIntExact(rs.getLong("position")),
						rs.getObject("student_id", UUID.class),
						rs.getString("full_name"),
						rs.getString("registration_number"),
						rs.getString("email"),
						rs.getLong("xp"),
						rs.getLong("stars"),
						toInstant(rs.getObject("first_completion_at", OffsetDateTime.class))
				)).list();
	}

	private Map<UUID, List<TeacherReportAttemptAnswerResponse>> loadAnswers(List<UUID> attemptIds) {
		if (attemptIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, List<TeacherReportAttemptAnswerResponse>> result = new HashMap<>();
		jdbcClient.sql("""
				select aqs.attempt_id, aqs.id as snapshot_id, aqs.position, aqs.prompt, aqs.type,
				       aa.boolean_value, aa.numeric_value, aa.correct, aa.answered_at,
				       aqs.correct_boolean, aqs.correct_numeric_value, aqs.explanation,
				       array(select aos.source_option_id
				             from attempt_answer_selected_options selected
				             join attempt_option_snapshots aos on aos.id = selected.option_snapshot_id
				             where selected.answer_id = aa.id order by aos.position) as selected_option_ids,
				       array(select aos.source_option_id from attempt_option_snapshots aos
				             where aos.question_snapshot_id = aqs.id and aos.correct order by aos.position) as correct_option_ids
				from attempt_question_snapshots aqs
				join attempt_answers aa on aa.question_snapshot_id = aqs.id
				where aqs.attempt_id in (:attemptIds)
				order by aqs.attempt_id, aqs.position
				""").param("attemptIds", attemptIds).query((rs, rowNum) -> {
			UUID attemptId = rs.getObject("attempt_id", UUID.class);
			QuestionType type = QuestionType.valueOf(rs.getString("type"));
			TeacherReportAttemptAnswerResponse answer = new TeacherReportAttemptAnswerResponse(
					rs.getObject("snapshot_id", UUID.class),
					rs.getInt("position"),
					rs.getString("prompt"),
					type,
					answerValue(type, uuidList(rs.getArray("selected_option_ids")),
							rs.getObject("boolean_value", Boolean.class), rs.getBigDecimal("numeric_value")),
					rs.getBoolean("correct"),
					answerValue(type, uuidList(rs.getArray("correct_option_ids")),
							rs.getObject("correct_boolean", Boolean.class), rs.getBigDecimal("correct_numeric_value")),
					rs.getString("explanation"),
					toInstant(rs.getObject("answered_at", OffsetDateTime.class))
			);
			result.computeIfAbsent(attemptId, ignored -> new ArrayList<>()).add(answer);
			return answer;
		}).list();
		return result;
	}

	private long countAttempts(ReportFilter filter, UUID studentId) {
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				select count(*) from attempts a
				join lesson_assignments la on la.id = a.assignment_id
				where la.room_id = :roomId and a.student_id = :studentId
				""" + attemptConditions(filter));
		return bindAttemptFilter(statement.param("roomId", filter.roomId()).param("studentId", studentId), filter)
				.query(Long.class).single();
	}

	private TeacherReportAttemptResponse withAnswers(
			TeacherReportAttemptResponse source,
			List<TeacherReportAttemptAnswerResponse> answers
	) {
		return new TeacherReportAttemptResponse(
				source.attemptId(), source.lessonId(), source.lessonTitle(), source.assignmentId(),
				source.sequence(), source.status(), source.startedAt(), source.submittedAt(),
				source.durationSeconds(), source.totalQuestions(), source.answeredQuestions(),
				source.correctAnswers(), source.scorePercent(), source.passed(), source.starsEarned(),
				source.xpCredited(), List.copyOf(answers)
		);
	}

	private AttemptAnswerValueResponse answerValue(
			QuestionType type,
			List<UUID> optionIds,
			Boolean booleanValue,
			BigDecimal numericValue
	) {
		return switch (type) {
			case SINGLE_CHOICE, MULTIPLE_CHOICE -> new AttemptAnswerValueResponse(optionIds, null, null);
			case TRUE_FALSE -> new AttemptAnswerValueResponse(null, booleanValue, null);
			case NUMERIC -> new AttemptAnswerValueResponse(
					null, null, numericValue == null ? null : numericValue.stripTrailingZeros().toPlainString()
			);
		};
	}

	private List<UUID> uuidList(Array array) throws SQLException {
		if (array == null) {
			return List.of();
		}
		UUID[] values = (UUID[]) array.getArray();
		return List.of(values);
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

	private Instant toInstant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

	private record RoomMetrics(long activeStudentCount, BigDecimal averageRoomXp) { }
	private record CompletionMetrics(BigDecimal completionRatePercent, BigDecimal averageBestStars) { }
}

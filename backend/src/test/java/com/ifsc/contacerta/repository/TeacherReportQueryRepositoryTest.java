package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.dto.attempt.AttemptAnswerValueResponse;
import com.ifsc.contacerta.dto.report.ReportAttemptSeriesItemResponse;
import com.ifsc.contacerta.dto.report.ReportLessonPerformanceResponse;
import com.ifsc.contacerta.dto.report.ReportScoreDistributionResponse;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.dto.report.TeacherReportStudentResponse;
import com.ifsc.contacerta.dto.report.TeacherReportAttemptResponse;
import com.ifsc.contacerta.dto.report.TeacherReportAttemptAnswerResponse;
import com.ifsc.contacerta.dto.report.TeacherReportRankingResponse;
import com.ifsc.contacerta.model.ReportFilter;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherReportQueryRepositoryTest extends PostgresIntegrationTest {

	@Autowired private TeacherReportQueryRepository repository;
	@Autowired private JdbcClient jdbcClient;

	@Test
	void deveCalcularOverviewComTentativasFinalizadasNoIntervalo() {
		Fixture fixture = createFixture();
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 1, "2026-08-10T10:00:00Z", 40, false, 1, 10);
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 2, "2026-08-10T11:00:00Z", 60, true, 2, 20);
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 3, "2026-08-11T10:00:00Z", 80, true, 3, 30);
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 4, "2026-08-11T11:00:00Z", 95, true, 3, 40);
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 5, "2026-07-01T10:00:00Z", 100, true, 3, 50);

		TeacherReportOverviewResponse result = repository.overview(new ReportFilter(
				fixture.roomId(), null,
				Instant.parse("2026-08-01T00:00:00Z"),
				Instant.parse("2026-08-20T00:00:00Z")
		));

		assertThat(result.activeStudentCount()).isEqualTo(2);
		assertThat(result.participatingStudentCount()).isEqualTo(1);
		assertThat(result.averageRoomXp()).isEqualByComparingTo("75.00");
		assertThat(result.completionRatePercent()).isEqualByComparingTo("25.00");
		assertThat(result.averageBestStars()).isEqualByComparingTo("3.00");
		assertThat(result.attemptSeries()).containsExactly(
				new ReportAttemptSeriesItemResponse(LocalDate.parse("2026-08-10"), 2),
				new ReportAttemptSeriesItemResponse(LocalDate.parse("2026-08-11"), 2)
		);
		assertThat(result.scoreDistribution()).isEqualTo(new ReportScoreDistributionResponse(1, 1, 1, 1));
		assertThat(result.lessonPerformance()).containsExactly(new ReportLessonPerformanceResponse(
				fixture.lessonId(), "Aula de porcentagem", 1, 4,
				new BigDecimal("68.75"), new BigDecimal("75.00")
		));
	}

	@Test
	void devePaginarAlunosAtivosComProgressoAtualEMetricasFiltradas() {
		Fixture fixture = createFixture();
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 1, "2026-08-10T10:00:00Z", 40, false, 1, 10);
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 2, "2026-08-11T10:00:00Z", 80, true, 3, 30);
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 3, "2026-07-01T10:00:00Z", 100, true, 3, 50);

		Page<TeacherReportStudentResponse> result = repository.students(
				new ReportFilter(
						fixture.roomId(), null,
						Instant.parse("2026-08-01T00:00:00Z"),
						Instant.parse("2026-08-20T00:00:00Z")
				),
				PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "totalXp"))
		);

		assertThat(result.getTotalElements()).isEqualTo(2);
		assertThat(result.getContent()).containsExactly(
				new TeacherReportStudentResponse(
						fixture.studentOneId(), "Aluno Um", "S1", "um@example.com",
						100, 1, 0, 0, 0, null, 2, new BigDecimal("60.00"), new BigDecimal("80.00")
				),
				new TeacherReportStudentResponse(
						fixture.studentTwoId(), "Aluno Dois", "S2", "dois@example.com",
						50, 1, 0, 0, 0, null, 0, new BigDecimal("0.00"), new BigDecimal("0.00")
				)
		);
	}

	@Test
	void deveListarTentativasFinalizadasComRespostasDosSnapshots() {
		Fixture fixture = createFixture();
		UUID attemptId = insertAttempt(
				fixture.assignmentId(), fixture.studentOneId(), 1,
				"2026-08-10T10:00:00Z", 80, true, 3, 30
		);
		UUID snapshotId = insertBooleanAnswer(fixture.lessonId(), attemptId, "Enunciado congelado", true, false);

		Page<TeacherReportAttemptResponse> result = repository.attempts(
				new ReportFilter(fixture.roomId(), null, null, null),
				fixture.studentOneId(),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt"))
		);

		assertThat(result.getTotalElements()).isEqualTo(1);
		TeacherReportAttemptResponse attempt = result.getContent().getFirst();
		assertThat(attempt.attemptId()).isEqualTo(attemptId);
		assertThat(attempt.lessonId()).isEqualTo(fixture.lessonId());
		assertThat(attempt.lessonTitle()).isEqualTo("Aula de porcentagem");
		assertThat(attempt.durationSeconds()).isEqualTo(600);
		assertThat(attempt.scorePercent()).isEqualTo(80);
		assertThat(attempt.answers()).containsExactly(new TeacherReportAttemptAnswerResponse(
				snapshotId, 1, "Enunciado congelado", QuestionType.TRUE_FALSE,
				new AttemptAnswerValueResponse(null, false, null),
				false,
				new AttemptAnswerValueResponse(null, true, null),
				"Explicação congelada",
				Instant.parse("2026-08-10T09:59:00Z")
		));
	}

	@Test
	void deveOrdenarTentativasPelaDirecaoSolicitada() {
		Fixture fixture = createFixture();
		UUID olderAttemptId = insertAttempt(
				fixture.assignmentId(), fixture.studentOneId(), 1,
				"2026-08-10T10:00:00Z", 60, true, 1, 10
		);
		UUID newerAttemptId = insertAttempt(
				fixture.assignmentId(), fixture.studentOneId(), 2,
				"2026-08-11T10:00:00Z", 80, true, 3, 20
		);

		Page<TeacherReportAttemptResponse> result = repository.attempts(
				new ReportFilter(fixture.roomId(), null, null, null),
				fixture.studentOneId(),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "submittedAt"))
		);

		assertThat(result.getContent())
				.extracting(TeacherReportAttemptResponse::attemptId)
				.containsExactly(olderAttemptId, newerAttemptId);
	}

	@Test
	void deveClassificarTodosOsAlunosAtivosPeloResultadoFiltrado() {
		Fixture fixture = createFixture();
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 1, "2026-08-10T10:00:00Z", 60, true, 1, 10);
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 2, "2026-08-11T10:00:00Z", 80, true, 3, 20);
		insertAttempt(fixture.assignmentId(), fixture.studentOneId(), 3, "2026-07-01T10:00:00Z", 100, true, 3, 100);

		List<TeacherReportRankingResponse> result = repository.ranking(new ReportFilter(
				fixture.roomId(), null,
				Instant.parse("2026-08-01T00:00:00Z"),
				Instant.parse("2026-08-20T00:00:00Z")
		));

		assertThat(result).containsExactly(
				new TeacherReportRankingResponse(
						1, fixture.studentOneId(), "Aluno Um", "S1", "um@example.com",
						30, 3, Instant.parse("2026-08-10T10:00:00Z")
				),
				new TeacherReportRankingResponse(
						2, fixture.studentTwoId(), "Aluno Dois", "S2", "dois@example.com",
						0, 0, null
				)
		);
	}

	@Test
	void deveCriarIndicesParciaisParaConsultasDeRelatorio() {
		List<String> indexNames = jdbcClient.sql("""
				select indexname from pg_indexes
				where schemaname = 'public' and tablename = 'attempts'
				""").query(String.class).list();

		assertThat(indexNames).contains(
				"idx_attempts_assignment_submitted_finalized",
				"idx_attempts_student_submitted_finalized"
		);
	}

	private Fixture createFixture() {
		UUID institutionId = UUID.randomUUID();
		UUID teacherId = UUID.randomUUID();
		UUID studentOneId = UUID.randomUUID();
		UUID studentTwoId = UUID.randomUUID();
		UUID roomId = UUID.randomUUID();
		UUID lessonId = UUID.randomUUID();
		UUID secondLessonId = UUID.randomUUID();
		UUID assignmentId = UUID.randomUUID();
		UUID secondAssignmentId = UUID.randomUUID();
		Instant now = Instant.parse("2026-08-01T00:00:00Z");

		jdbcClient.sql("""
				insert into institutions (id, name, cnpj, contact_email, contact_phone, active, created_at, updated_at)
				values (:id, 'IFSC', '11222333000181', 'ifsc@example.com', '+5548999999999', true, :now, :now)
		""").param("id", institutionId).param("now", Timestamp.from(now)).update();
		insertUser(teacherId, "TEACHER", "Professora Ana", "ana@example.com", "T1", institutionId, now);
		insertUser(studentOneId, "STUDENT", "Aluno Um", "um@example.com", "S1", institutionId, now);
		insertUser(studentTwoId, "STUDENT", "Aluno Dois", "dois@example.com", "S2", institutionId, now);
		jdbcClient.sql("""
				insert into rooms (id, teacher_id, institution_id, name, grade, passing_score_percent,
				 join_code_display, join_code_hash, created_at, updated_at)
				values (:id, :teacher, :institution, 'Sala A', 'HIGH_SCHOOL_1', 60,
				 'ABC123', :hash, :now, :now)
				""").param("id", roomId).param("teacher", teacherId).param("institution", institutionId)
				.param("hash", "a".repeat(64)).param("now", Timestamp.from(now)).update();
		insertMembership(roomId, studentOneId, now);
		insertMembership(roomId, studentTwoId, now);
		insertLesson(lessonId, teacherId, "Aula de porcentagem", now);
		insertLesson(secondLessonId, teacherId, "Aula de juros", now);
		insertAssignment(assignmentId, roomId, lessonId, 1, now);
		insertAssignment(secondAssignmentId, roomId, secondLessonId, 2, now);
		insertProgress(roomId, studentOneId, 100, now);
		insertProgress(roomId, studentTwoId, 50, now);
		return new Fixture(roomId, lessonId, assignmentId, studentOneId, studentTwoId);
	}

	private void insertUser(UUID id, String role, String name, String email, String registration, UUID institution, Instant now) {
		jdbcClient.sql("""
				insert into users (id, role, status, full_name, email, registration_number, institution_id,
				 must_change_password, created_at, updated_at)
				values (:id, :role, 'ACTIVE', :name, :email, :registration, :institution, false, :now, :now)
				""").param("id", id).param("role", role).param("name", name).param("email", email)
				.param("registration", registration).param("institution", institution)
				.param("now", Timestamp.from(now)).update();
	}

	private void insertMembership(UUID roomId, UUID studentId, Instant now) {
		jdbcClient.sql("""
				insert into room_memberships (id, room_id, student_id, status, joined_at, created_at, updated_at)
				values (:id, :room, :student, 'ACTIVE', :now, :now, :now)
				""").param("id", UUID.randomUUID()).param("room", roomId).param("student", studentId)
				.param("now", Timestamp.from(now)).update();
	}

	private void insertLesson(UUID id, UUID teacherId, String title, Instant now) {
		jdbcClient.sql("""
				insert into lessons (id, teacher_id, title, theory_markdown, status, created_at, updated_at)
				values (:id, :teacher, :title, '', 'PUBLISHED', :now, :now)
		""").param("id", id).param("teacher", teacherId).param("title", title)
				.param("now", Timestamp.from(now)).update();
	}

	private void insertAssignment(UUID id, UUID roomId, UUID lessonId, int position, Instant now) {
		jdbcClient.sql("""
				insert into lesson_assignments (id, room_id, lesson_id, position, status, shuffle_questions,
				 shuffle_options, created_at, updated_at)
				values (:id, :room, :lesson, :position, 'PUBLISHED', true, true, :now, :now)
				""").param("id", id).param("room", roomId).param("lesson", lessonId).param("position", position)
				.param("now", Timestamp.from(now)).update();
	}

	private void insertProgress(UUID roomId, UUID studentId, int xp, Instant now) {
		jdbcClient.sql("""
				insert into room_student_progress (id, room_id, student_id, total_xp, level, total_best_stars,
				 completed_assignment_count, passed_assignment_count, created_at, updated_at)
				values (:id, :room, :student, :xp, 1, 0, 0, 0, :now, :now)
				""").param("id", UUID.randomUUID()).param("room", roomId).param("student", studentId)
				.param("xp", xp).param("now", Timestamp.from(now)).update();
	}

	private UUID insertAttempt(UUID assignmentId, UUID studentId, int sequence, String submittedAt,
			int score, boolean passed, int stars, int xp) {
		Instant submitted = Instant.parse(submittedAt);
		UUID attemptId = UUID.randomUUID();
		jdbcClient.sql("""
				insert into attempts (id, assignment_id, student_id, sequence, status, started_at, submitted_at,
				 total_questions, answered_questions, correct_answers, score_percent, passed, stars, xp_credited,
				 created_at, updated_at)
				values (:id, :assignment, :student, :sequence, 'SUBMITTED', :started, :submitted,
				 100, 100, :score, :score, :passed, :stars, :xp, :submitted, :submitted)
				""").param("id", attemptId).param("assignment", assignmentId).param("student", studentId)
				.param("sequence", sequence).param("started", Timestamp.from(submitted.minusSeconds(600)))
				.param("submitted", Timestamp.from(submitted))
				.param("score", score).param("passed", passed).param("stars", stars).param("xp", xp).update();
		return attemptId;
	}

	private UUID insertBooleanAnswer(UUID lessonId, UUID attemptId, String prompt, boolean correctValue, boolean answer) {
		UUID questionId = UUID.randomUUID();
		UUID snapshotId = UUID.randomUUID();
		jdbcClient.sql("""
				insert into questions (id, lesson_id, type, prompt, explanation, position, active,
				 correct_boolean, created_at, updated_at)
				values (:id, :lesson, 'TRUE_FALSE', 'Enunciado atual', 'Explicação atual', 1, true,
				 :correct, now(), now())
				""").param("id", questionId).param("lesson", lessonId).param("correct", correctValue).update();
		jdbcClient.sql("""
				insert into attempt_question_snapshots (id, attempt_id, question_id, type, prompt, explanation,
				 position, correct_boolean)
				values (:id, :attempt, :question, 'TRUE_FALSE', :prompt, 'Explicação congelada', 1, :correct)
				""").param("id", snapshotId).param("attempt", attemptId).param("question", questionId)
				.param("prompt", prompt).param("correct", correctValue).update();
		jdbcClient.sql("""
				insert into attempt_answers (id, question_snapshot_id, boolean_value, correct, answered_at)
				values (:id, :snapshot, :answer, :correct, :answeredAt)
				""").param("id", UUID.randomUUID()).param("snapshot", snapshotId).param("answer", answer)
				.param("correct", answer == correctValue)
				.param("answeredAt", Timestamp.from(Instant.parse("2026-08-10T09:59:00Z"))).update();
		return snapshotId;
	}

	private record Fixture(
			UUID roomId,
			UUID lessonId,
			UUID assignmentId,
			UUID studentOneId,
			UUID studentTwoId
	) { }
}

package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.dto.report.ReportAttemptSeriesItemResponse;
import com.ifsc.contacerta.dto.report.ReportLessonPerformanceResponse;
import com.ifsc.contacerta.dto.report.ReportScoreDistributionResponse;
import com.ifsc.contacerta.dto.report.TeacherReportOverviewResponse;
import com.ifsc.contacerta.model.ReportFilter;
import com.ifsc.contacerta.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
		return new Fixture(roomId, lessonId, assignmentId, studentOneId);
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

	private void insertAttempt(UUID assignmentId, UUID studentId, int sequence, String submittedAt,
			int score, boolean passed, int stars, int xp) {
		Instant submitted = Instant.parse(submittedAt);
		jdbcClient.sql("""
				insert into attempts (id, assignment_id, student_id, sequence, status, started_at, submitted_at,
				 total_questions, answered_questions, correct_answers, score_percent, passed, stars, xp_credited,
				 created_at, updated_at)
				values (:id, :assignment, :student, :sequence, 'SUBMITTED', :started, :submitted,
				 100, 100, :score, :score, :passed, :stars, :xp, :submitted, :submitted)
				""").param("id", UUID.randomUUID()).param("assignment", assignmentId).param("student", studentId)
				.param("sequence", sequence).param("started", Timestamp.from(submitted.minusSeconds(600)))
				.param("submitted", Timestamp.from(submitted))
				.param("score", score).param("passed", passed).param("stars", stars).param("xp", xp).update();
	}

	private record Fixture(UUID roomId, UUID lessonId, UUID assignmentId, UUID studentOneId) { }
}

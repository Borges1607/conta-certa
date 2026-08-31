package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.report.ReportPeriod;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.ReportFilter;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TeacherReportFilterFactory {

	private static final long DEFAULT_PERIOD_DAYS = 30;

	private final UserRepository userRepository;
	private final RoomRepository roomRepository;
	private final LessonRepository lessonRepository;
	private final LessonAssignmentRepository assignmentRepository;
	private final RoomMembershipRepository membershipRepository;
	private final Clock clock;

	public ReportFilter create(
			UUID teacherId,
			UUID roomId,
			UUID lessonId,
			ReportPeriod period,
			Instant from,
			Instant to
	) {
		requireActiveTeacher(teacherId);
		requireOwnedRoom(teacherId, roomId);
		if (lessonId != null) {
			requireAssignedLesson(teacherId, roomId, lessonId);
		}
		return resolvePeriod(roomId, lessonId, period, from, to);
	}

	public void requireActiveStudent(UUID roomId, UUID studentId) {
		if (!membershipRepository.existsByRoomIdAndStudentIdAndStatus(
				roomId, studentId, MembershipStatus.ACTIVE
		)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student was not found.");
		}
	}

	private ReportFilter resolvePeriod(
			UUID roomId,
			UUID lessonId,
			ReportPeriod period,
			Instant from,
			Instant to
	) {
		if (period == ReportPeriod.ALL) {
			if (from != null || to != null) {
				throw validationError("Period ALL cannot be combined with from or to.");
			}
			return new ReportFilter(roomId, lessonId, null, null);
		}
		if ((from == null) != (to == null)) {
			throw validationError("From and to must be provided together.");
		}
		if (from != null) {
			if (!from.isBefore(to)) {
				throw validationError("From must be before to.");
			}
			return new ReportFilter(roomId, lessonId, from, to);
		}
		Instant now = clock.instant();
		return new ReportFilter(roomId, lessonId, now.minus(DEFAULT_PERIOD_DAYS, ChronoUnit.DAYS), now);
	}

	private void requireActiveTeacher(UUID teacherId) {
		User teacher = userRepository.findById(teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher was not found."
		));
		if (teacher.getRole() != Role.TEACHER) {
			throw new ApiException(
					HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "A teacher account is required."
			);
		}
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(
					HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Teacher account is inactive."
			);
		}
	}

	private void requireOwnedRoom(UUID teacherId, UUID roomId) {
		roomRepository.findByIdAndTeacherId(roomId, teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room was not found."
		));
	}

	private void requireAssignedLesson(UUID teacherId, UUID roomId, UUID lessonId) {
		boolean owned = lessonRepository.findByIdAndTeacherId(lessonId, teacherId).isPresent();
		if (!owned || !assignmentRepository.existsByRoomIdAndLessonId(roomId, lessonId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson was not found.");
		}
	}

	private ApiException validationError(String detail) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", detail);
	}
}

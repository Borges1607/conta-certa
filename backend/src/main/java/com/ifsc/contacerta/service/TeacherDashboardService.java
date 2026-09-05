package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.teacher.TeacherDashboardResponse;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherDashboardService {

	private final UserRepository userRepository;
	private final RoomRepository roomRepository;
	private final RoomMembershipRepository membershipRepository;
	private final LessonRepository lessonRepository;
	private final LessonAssignmentRepository assignmentRepository;

	@Transactional(readOnly = true)
	public TeacherDashboardResponse get(UUID teacherId) {
		requireActiveTeacher(teacherId);
		return new TeacherDashboardResponse(
				new TeacherDashboardResponse.RoomCounts(
						roomRepository.countByTeacherId(teacherId),
						roomRepository.countByTeacherIdAndArchivedAtIsNull(teacherId),
						roomRepository.countByTeacherIdAndArchivedAtIsNotNull(teacherId)
				),
				new TeacherDashboardResponse.StudentCounts(
						membershipRepository.countDistinctStudentsByTeacherId(teacherId),
						membershipRepository.countByRoomTeacherIdAndStatus(teacherId, MembershipStatus.ACTIVE)
				),
				new TeacherDashboardResponse.LessonCounts(
						lessonRepository.countByTeacherId(teacherId),
						lessonRepository.countByTeacherIdAndStatus(teacherId, ContentStatus.PUBLISHED),
						lessonRepository.countByTeacherIdAndStatus(teacherId, ContentStatus.DRAFT)
				),
				new TeacherDashboardResponse.AssignmentCounts(
						assignmentRepository.countByRoomTeacherId(teacherId),
						assignmentRepository.countByRoomTeacherIdAndStatus(teacherId, ContentStatus.PUBLISHED)
				)
		);
	}

	private void requireActiveTeacher(UUID teacherId) {
		User teacher = userRepository.findById(teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher was not found."
		));
		if (teacher.getRole() != Role.TEACHER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "A teacher account is required.");
		}
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Teacher account is inactive.");
		}
	}
}

package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.lesson.CreateLessonRequest;
import com.ifsc.contacerta.dto.lesson.LessonDetailResponse;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonService {

	private final UserRepository userRepository;
	private final LessonRepository lessonRepository;

	@Transactional
	public LessonDetailResponse create(UUID teacherId, CreateLessonRequest request) {
		User teacher = requireActiveTeacher(teacherId);
		Lesson lesson = lessonRepository.save(new Lesson(
				request.title(), request.summary(), request.theoryMarkdown(), teacher
		));
		return toDetailResponse(lesson);
	}

	private User requireActiveTeacher(UUID teacherId) {
		User teacher = userRepository.findById(teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher was not found."
		));
		if (teacher.getRole() != Role.TEACHER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TEACHER_REQUIRED", "A teacher account is required.");
		}
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Teacher account is inactive.");
		}
		return teacher;
	}

	private LessonDetailResponse toDetailResponse(Lesson lesson) {
		return new LessonDetailResponse(
				lesson.getId(),
				lesson.getTitle(),
				lesson.getSummary(),
				lesson.getTheoryMarkdown(),
				lesson.getStatus(),
				0,
				0,
				lesson.getCreatedAt(),
				lesson.getUpdatedAt(),
				lesson.getVersion()
		);
	}
}

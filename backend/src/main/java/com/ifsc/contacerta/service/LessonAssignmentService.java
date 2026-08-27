package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.assignment.CreateLessonAssignmentRequest;
import com.ifsc.contacerta.dto.assignment.LessonAssignmentOrderItem;
import com.ifsc.contacerta.dto.assignment.LessonAssignmentOrderRequest;
import com.ifsc.contacerta.dto.assignment.LessonAssignmentResponse;
import com.ifsc.contacerta.dto.assignment.UpdateLessonAssignmentRequest;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.LessonAssignment;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.ContentStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.LessonAssignmentRepository;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import com.ifsc.contacerta.repository.RoomRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonAssignmentService {

	private static final int DEFAULT_TIME_LIMIT_MINUTES = 30;
	private static final int DEFAULT_MAX_ATTEMPTS = 3;

	private final UserRepository userRepository;
	private final RoomRepository roomRepository;
	private final LessonRepository lessonRepository;
	private final LessonAssignmentRepository assignmentRepository;
	private final QuestionRepository questionRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<LessonAssignmentResponse> list(UUID teacherId, UUID roomId) {
		requireActiveTeacher(teacherId);
		requireMutableRoom(teacherId, roomId);
		return assignmentRepository.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(roomId, teacherId)
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public LessonAssignmentResponse create(
			UUID teacherId,
			UUID roomId,
			CreateLessonAssignmentRequest request
	) {
		requireActiveTeacher(teacherId);
		Room room = requireMutableRoom(teacherId, roomId);
		Lesson lesson = requireOwnedLesson(teacherId, request.lessonId());
		if (assignmentRepository.existsByRoomIdAndLessonId(roomId, lesson.getId())) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"LESSON_ALREADY_ASSIGNED",
					"The lesson is already assigned to this room."
			);
		}

		ContentStatus status = request.status() == null ? ContentStatus.DRAFT : request.status();
		if (status == ContentStatus.ARCHIVED) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"ASSIGNMENT_ARCHIVED",
					"An assignment cannot be created as archived."
			);
		}
		Integer timeLimitMinutes = resolveCreateInteger(
				request.timeLimitMinutes(), DEFAULT_TIME_LIMIT_MINUTES
		);
		Integer maxAttempts = resolveCreateInteger(request.maxAttempts(), DEFAULT_MAX_ATTEMPTS);
		Integer questionCount = resolveCreateInteger(request.questionCount(), null);
		long activeQuestionCount = questionRepository.countByLessonIdAndActiveTrue(lesson.getId());
		validateConfiguration(
				status,
				lesson,
				request.availableFrom(),
				request.dueAt(),
				questionCount,
				activeQuestionCount
		);

		List<LessonAssignment> existing = new ArrayList<>(assignmentRepository.findByRoomIdForUpdate(roomId));
		int position = request.position() == null ? existing.size() + 1 : request.position();
		if (position < 1 || position > existing.size() + 1) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_ASSIGNMENT_POSITION",
					"Assignment position is outside the room path."
			);
		}
		openPosition(existing, position);
		LessonAssignment assignment = new LessonAssignment(
				room,
				lesson,
				position,
				request.availableFrom(),
				request.dueAt(),
				timeLimitMinutes,
				maxAttempts,
				questionCount,
				request.shuffleQuestions() == null || request.shuffleQuestions(),
				request.shuffleOptions() == null || request.shuffleOptions()
		);
		if (status == ContentStatus.PUBLISHED) {
			assignment.publish();
		}
		return toResponse(assignmentRepository.save(assignment), activeQuestionCount);
	}

	@Transactional
	public LessonAssignmentResponse update(
			UUID teacherId,
			UUID roomId,
			UUID assignmentId,
			UpdateLessonAssignmentRequest request
	) {
		requireActiveTeacher(teacherId);
		requireMutableRoom(teacherId, roomId);
		LessonAssignment assignment = requireOwnedAssignment(teacherId, roomId, assignmentId);
		requireCurrentVersion(assignment, request.version());
		if (assignment.getStatus() == ContentStatus.ARCHIVED) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"ASSIGNMENT_ARCHIVED",
					"Archived assignments are read-only."
			);
		}

		ContentStatus status = request.status() == null ? assignment.getStatus() : request.status();
		Instant availableFrom = resolveInstant(request.availableFrom(), assignment.getAvailableFrom());
		Instant dueAt = resolveInstant(request.dueAt(), assignment.getDueAt());
		Integer timeLimitMinutes = resolveUpdateInteger(
				request.timeLimitMinutes(), assignment.getTimeLimitMinutes()
		);
		Integer maxAttempts = resolveUpdateInteger(request.maxAttempts(), assignment.getMaxAttempts());
		Integer questionCount = resolveUpdateInteger(request.questionCount(), assignment.getQuestionCount());
		boolean shuffleQuestions = request.shuffleQuestions() == null
				? assignment.isShuffleQuestions()
				: request.shuffleQuestions();
		boolean shuffleOptions = request.shuffleOptions() == null
				? assignment.isShuffleOptions()
				: request.shuffleOptions();
		long activeQuestionCount = questionRepository.countByLessonIdAndActiveTrue(
				assignment.getLesson().getId()
		);
		validateConfiguration(
				status,
				assignment.getLesson(),
				availableFrom,
				dueAt,
				questionCount,
				activeQuestionCount
		);
		assignment.configure(
				status,
				availableFrom,
				dueAt,
				timeLimitMinutes,
				maxAttempts,
				questionCount,
				shuffleQuestions,
				shuffleOptions
		);
		assignmentRepository.flush();
		return toResponse(assignment, activeQuestionCount);
	}

	@Transactional
	public void delete(UUID teacherId, UUID roomId, UUID assignmentId, long version) {
		requireActiveTeacher(teacherId);
		requireMutableRoom(teacherId, roomId);
		LessonAssignment assignment = requireOwnedAssignment(teacherId, roomId, assignmentId);
		requireCurrentVersion(assignment, version);
		if (!canDelete(assignment)) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"ASSIGNMENT_ALREADY_IN_USE",
					"Assignments already available to students must be archived."
			);
		}

		List<LessonAssignment> assignments = new ArrayList<>(assignmentRepository.findByRoomIdForUpdate(roomId));
		assignmentRepository.delete(assignment);
		assignmentRepository.flush();
		List<LessonAssignment> remaining = assignments.stream()
				.filter(current -> !current.getId().equals(assignmentId))
				.toList();
		normalizePositions(remaining, assignments.size());
	}

	@Transactional
	public List<LessonAssignmentResponse> reorder(
			UUID teacherId,
			UUID roomId,
			LessonAssignmentOrderRequest request
	) {
		requireActiveTeacher(teacherId);
		requireMutableRoom(teacherId, roomId);
		List<LessonAssignment> assignments = new ArrayList<>(assignmentRepository.findByRoomIdForUpdate(roomId));
		validateCompleteOrder(assignments, request);

		Map<UUID, LessonAssignment> assignmentsById = new HashMap<>();
		for (LessonAssignment assignment : assignments) {
			assignmentsById.put(assignment.getId(), assignment);
		}
		for (LessonAssignmentOrderItem item : request.assignments()) {
			requireCurrentVersion(assignmentsById.get(item.assignmentId()), item.version());
		}

		int size = assignments.size();
		for (int index = 0; index < size; index++) {
			assignments.get(index).moveTo(size + index + 1);
		}
		assignmentRepository.flush();
		List<LessonAssignment> reordered = new ArrayList<>(size);
		for (int index = 0; index < request.assignments().size(); index++) {
			LessonAssignment assignment = assignmentsById.get(request.assignments().get(index).assignmentId());
			assignment.moveTo(index + 1);
			reordered.add(assignment);
		}
		assignmentRepository.flush();
		return reordered.stream().map(this::toResponse).toList();
	}

	private void validateCompleteOrder(
			List<LessonAssignment> assignments,
			LessonAssignmentOrderRequest request
	) {
		if (request == null || request.assignments() == null || request.assignments().isEmpty()
				|| request.assignments().size() != assignments.size()) {
			throw invalidOrder();
		}
		Set<UUID> persistedIds = new HashSet<>();
		for (LessonAssignment assignment : assignments) {
			persistedIds.add(assignment.getId());
		}
		Set<UUID> requestedIds = new HashSet<>();
		for (LessonAssignmentOrderItem item : request.assignments()) {
			if (item == null || item.assignmentId() == null || !requestedIds.add(item.assignmentId())) {
				throw invalidOrder();
			}
		}
		if (!requestedIds.equals(persistedIds)) {
			throw invalidOrder();
		}
	}

	private ApiException invalidOrder() {
		return new ApiException(
				HttpStatus.CONFLICT,
				"INVALID_ASSIGNMENT_ORDER",
				"The complete room assignment order is required."
		);
	}

	private void openPosition(List<LessonAssignment> assignments, int insertionPosition) {
		if (assignments.isEmpty()) {
			return;
		}
		int size = assignments.size();
		for (int index = 0; index < size; index++) {
			assignments.get(index).moveTo(size + index + 1);
		}
		assignmentRepository.flush();
		for (int index = 0; index < size; index++) {
			int originalPosition = index + 1;
			assignments.get(index).moveTo(
					originalPosition < insertionPosition ? originalPosition : originalPosition + 1
			);
		}
		assignmentRepository.flush();
	}

	private Integer resolveCreateInteger(JsonNode node, Integer defaultValue) {
		if (node == null) {
			return defaultValue;
		}
		if (node.isNull()) {
			return null;
		}
		if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() <= 0) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_ASSIGNMENT_LIMIT",
					"Assignment limits must be positive integers or null."
			);
		}
		return node.intValue();
	}

	private Integer resolveUpdateInteger(JsonNode node, Integer currentValue) {
		if (node == null) {
			return currentValue;
		}
		if (node.isNull()) {
			return null;
		}
		if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() <= 0) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_ASSIGNMENT_LIMIT",
					"Assignment limits must be positive integers or null."
			);
		}
		return node.intValue();
	}

	private Instant resolveInstant(JsonNode node, Instant currentValue) {
		if (node == null) {
			return currentValue;
		}
		if (node.isNull()) {
			return null;
		}
		if (!node.isString()) {
			throw invalidDates();
		}
		try {
			return Instant.parse(node.stringValue());
		} catch (DateTimeParseException exception) {
			throw invalidDates();
		}
	}

	private boolean canDelete(LessonAssignment assignment) {
		if (assignment.getStatus() == ContentStatus.DRAFT) {
			return true;
		}
		return assignment.getStatus() == ContentStatus.PUBLISHED
				&& assignment.getAvailableFrom() != null
				&& assignment.getAvailableFrom().isAfter(clock.instant());
	}

	private void normalizePositions(List<LessonAssignment> assignments, int previousSize) {
		for (int index = 0; index < assignments.size(); index++) {
			assignments.get(index).moveTo(previousSize + index + 1);
		}
		assignmentRepository.flush();
		for (int index = 0; index < assignments.size(); index++) {
			assignments.get(index).moveTo(index + 1);
		}
		assignmentRepository.flush();
	}

	private void validateConfiguration(
			ContentStatus status,
			Lesson lesson,
			Instant availableFrom,
			Instant dueAt,
			Integer questionCount,
			long activeQuestionCount
	) {
		if (availableFrom != null && dueAt != null && !dueAt.isAfter(availableFrom)) {
			throw invalidDates();
		}
		if (status == ContentStatus.PUBLISHED && availableFrom == null
				&& dueAt != null && !dueAt.isAfter(clock.instant())) {
			throw invalidDates();
		}
		if (questionCount != null && questionCount > activeQuestionCount) {
			throw insufficientQuestions();
		}
		if (status == ContentStatus.PUBLISHED) {
			if (lesson.getStatus() != ContentStatus.PUBLISHED) {
				throw new ApiException(
						HttpStatus.UNPROCESSABLE_CONTENT,
						"LESSON_NOT_PUBLISHED",
						"Only published lessons can be published in a room."
				);
			}
			if (activeQuestionCount == 0) {
				throw insufficientQuestions();
			}
		}
	}

	private ApiException invalidDates() {
		return new ApiException(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"INVALID_ASSIGNMENT_DATES",
				"Assignment availability dates are invalid."
		);
	}

	private ApiException insufficientQuestions() {
		return new ApiException(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"INSUFFICIENT_ACTIVE_QUESTIONS",
				"The lesson does not have enough active questions."
		);
	}

	private User requireActiveTeacher(UUID teacherId) {
		User teacher = userRepository.findById(teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND,
				"TEACHER_NOT_FOUND",
				"Teacher was not found."
		));
		if (teacher.getRole() != Role.TEACHER) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"TEACHER_REQUIRED",
					"A teacher account is required."
			);
		}
		if (teacher.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"ACCOUNT_INACTIVE",
					"Teacher account is inactive."
			);
		}
		return teacher;
	}

	private Room requireMutableRoom(UUID teacherId, UUID roomId) {
		Room room = roomRepository.findByIdAndTeacherId(roomId, teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND,
				"ROOM_NOT_FOUND",
				"Room was not found."
		));
		if (room.isArchived()) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"ROOM_ARCHIVED",
					"Archived rooms are read-only."
			);
		}
		return room;
	}

	private Lesson requireOwnedLesson(UUID teacherId, UUID lessonId) {
		return lessonRepository.findByIdAndTeacherId(lessonId, teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND,
				"LESSON_NOT_FOUND",
				"Lesson was not found."
		));
	}

	private LessonAssignment requireOwnedAssignment(UUID teacherId, UUID roomId, UUID assignmentId) {
		return assignmentRepository.findByIdAndRoomIdAndRoomTeacherId(assignmentId, roomId, teacherId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"ASSIGNMENT_NOT_FOUND",
						"Lesson assignment was not found."
				));
	}

	private void requireCurrentVersion(LessonAssignment assignment, Long version) {
		if (version == null || version != assignment.getVersion()) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"VERSION_CONFLICT",
					"The lesson assignment was changed by another request."
			);
		}
	}

	private LessonAssignmentResponse toResponse(LessonAssignment assignment) {
		return toResponse(
				assignment,
				questionRepository.countByLessonIdAndActiveTrue(assignment.getLesson().getId())
		);
	}

	private LessonAssignmentResponse toResponse(LessonAssignment assignment, long activeQuestionCount) {
		return new LessonAssignmentResponse(
				assignment.getId(),
				assignment.getRoom().getId(),
				assignment.getLesson().getId(),
				assignment.getLesson().getTitle(),
				assignment.getPosition(),
				assignment.getStatus(),
				assignment.getAvailableFrom(),
				assignment.getDueAt(),
				assignment.getTimeLimitMinutes(),
				assignment.getMaxAttempts(),
				assignment.getQuestionCount(),
				assignment.isShuffleQuestions(),
				assignment.isShuffleOptions(),
				activeQuestionCount,
				assignment.getCreatedAt(),
				assignment.getUpdatedAt(),
				assignment.getVersion()
		);
	}
}

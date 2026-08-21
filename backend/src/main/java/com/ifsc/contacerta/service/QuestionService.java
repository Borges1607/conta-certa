package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.question.CreateQuestionRequest;
import com.ifsc.contacerta.dto.question.QuestionOptionRequest;
import com.ifsc.contacerta.dto.question.QuestionOptionResponse;
import com.ifsc.contacerta.dto.question.QuestionResponse;
import com.ifsc.contacerta.dto.question.QuestionOrderRequest;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.Question;
import com.ifsc.contacerta.entity.QuestionOptionData;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionService {

	private final LessonRepository lessonRepository;
	private final QuestionRepository questionRepository;

	@Transactional
	public QuestionResponse create(UUID teacherId, UUID lessonId, CreateQuestionRequest request) {
		Lesson lesson = lessonRepository.findByIdAndTeacherId(lessonId, teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson was not found."
		));
		validate(request);
		List<QuestionOptionData> options = request.options() == null
				? List.of()
				: request.options().stream().map(option -> new QuestionOptionData(option.text(), option.correct())).toList();
		Question question = Question.choice(lesson, request.type(), request.prompt(), request.explanation(), options);
		if (request.type() == QuestionType.TRUE_FALSE) {
			question.configureBoolean(request.correctBoolean());
		}
		if (request.type() == QuestionType.NUMERIC) {
			question.configureNumeric(
					request.correctNumericValue(), request.absoluteTolerance(), request.unit(), request.decimalPlaces()
			);
		}
		return toResponse(questionRepository.save(question));
	}

	@Transactional(readOnly = true)
	public List<QuestionResponse> list(UUID teacherId, UUID lessonId) {
		lessonRepository.findByIdAndTeacherId(lessonId, teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson was not found."
		));
		return questionRepository.findByLessonIdOrderByPositionAsc(lessonId).stream().map(this::toResponse).toList();
	}

	@Transactional
	public void delete(UUID teacherId, UUID questionId) {
		Question question = requireOwnedQuestion(teacherId, questionId);
		questionRepository.delete(question);
	}

	@Transactional
	public List<QuestionResponse> reorder(UUID teacherId, UUID lessonId, QuestionOrderRequest request) {
		lessonRepository.findByIdAndTeacherId(lessonId, teacherId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson was not found."));
		List<Question> questions = questionRepository.findByLessonIdOrderByPositionAsc(lessonId);
		if (questions.size() != request.questionIds().size() || !questions.stream().map(Question::getId).collect(java.util.stream.Collectors.toSet()).equals(new java.util.HashSet<>(request.questionIds()))) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_QUESTION_ORDER", "Question order is invalid.");
		}
		for (int index = 0; index < request.questionIds().size(); index++) {
			UUID questionId = request.questionIds().get(index);
			questions.stream().filter(question -> question.getId().equals(questionId)).findFirst().orElseThrow().moveTo(index + 1);
		}
		return questionRepository.findByLessonIdOrderByPositionAsc(lessonId).stream().map(this::toResponse).toList();
	}

	private void validate(CreateQuestionRequest request) {
		if (request.type() == QuestionType.SINGLE_CHOICE) {
			long correctOptions = request.options() == null ? 0 : request.options().stream().filter(QuestionOptionRequest::correct).count();
			if (request.options() == null || request.options().size() < 2 || correctOptions != 1) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_QUESTION_OPTIONS", "Single choice needs exactly one correct option.");
			}
		}
		if (request.type() == QuestionType.MULTIPLE_CHOICE) {
			long correctOptions = request.options() == null ? 0 : request.options().stream().filter(QuestionOptionRequest::correct).count();
			if (request.options() == null || request.options().size() < 2 || correctOptions < 2) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_QUESTION_OPTIONS", "Multiple choice needs at least two correct options.");
			}
		}
		if (request.type() == QuestionType.TRUE_FALSE && request.correctBoolean() == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_TRUE_FALSE_ANSWER", "True or false questions need a correct answer.");
		}
		if (request.type() == QuestionType.NUMERIC && (request.correctNumericValue() == null || request.absoluteTolerance() == null
				|| request.absoluteTolerance().signum() < 0 || request.unit() == null || request.decimalPlaces() == null
				|| request.decimalPlaces() < 0)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_NUMERIC_CONFIGURATION", "Numeric question configuration is invalid.");
		}
	}

	private Question requireOwnedQuestion(UUID teacherId, UUID questionId) {
		return questionRepository.findByIdAndLessonTeacherId(questionId, teacherId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question was not found."));
	}

	private QuestionResponse toResponse(Question question) {
		return new QuestionResponse(
				question.getId(), question.getLesson().getId(), question.getPrompt(), question.getType(), question.getExplanation(),
				question.getPosition(), !question.isActive(), question.getVersion(),
				question.getOptions().stream().map(option -> new QuestionOptionResponse(option.getId(), option.getText(), option.isCorrect())).toList(),
				question.getCorrectBoolean(), question.getCorrectNumericValue(), question.getAbsoluteTolerance(), question.getUnit(), question.getDecimalPlaces()
		);
	}
}

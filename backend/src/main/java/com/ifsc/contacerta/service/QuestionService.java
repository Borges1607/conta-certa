package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.question.CreateQuestionRequest;
import com.ifsc.contacerta.dto.question.QuestionOptionRequest;
import com.ifsc.contacerta.dto.question.QuestionOptionResponse;
import com.ifsc.contacerta.dto.question.QuestionResponse;
import com.ifsc.contacerta.dto.question.QuestionOrderRequest;
import com.ifsc.contacerta.dto.question.DuplicateQuestionRequest;
import com.ifsc.contacerta.dto.question.UpdateQuestionRequest;
import com.ifsc.contacerta.entity.Lesson;
import com.ifsc.contacerta.entity.Question;
import com.ifsc.contacerta.entity.QuestionOptionData;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.NumericUnit;
import com.ifsc.contacerta.model.QuestionType;
import com.ifsc.contacerta.repository.LessonRepository;
import com.ifsc.contacerta.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionService {

	private final LessonRepository lessonRepository;
	private final QuestionRepository questionRepository;

	@Transactional
	public QuestionResponse create(UUID teacherId, UUID lessonId, CreateQuestionRequest request) {
		Lesson lesson = requireOwnedLessonForUpdate(teacherId, lessonId);
		int position = questionRepository.findMaximumPositionByLessonId(lessonId) + 1;
		validate(request);
		List<QuestionOptionData> options = request.options() == null
				? List.of()
				: request.options().stream().map(option -> new QuestionOptionData(option.id(), option.text(), option.correct())).toList();
		Question question = Question.create(
				lesson, request.type(), request.prompt(), request.explanation(), options, position
		);
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
	public QuestionResponse duplicate(UUID teacherId, UUID questionId, DuplicateQuestionRequest request) {
		Question source = requireOwnedQuestion(teacherId, questionId);
		Lesson target = requireOwnedLessonForUpdate(teacherId, request.targetLessonId());
		int position = questionRepository.findMaximumPositionByLessonId(target.getId()) + 1;
		List<QuestionOptionData> options = source.getOptions().stream().map(option -> new QuestionOptionData(option.getText(), option.isCorrect())).toList();
		Question copy = Question.create(
				target, source.getType(), source.getPrompt(), source.getExplanation(), options, position
		);
		if (source.getType() == QuestionType.TRUE_FALSE) copy.configureBoolean(source.getCorrectBoolean());
		if (source.getType() == QuestionType.NUMERIC) copy.configureNumeric(source.getCorrectNumericValue(), source.getAbsoluteTolerance(), source.getUnit(), source.getDecimalPlaces());
		return toResponse(questionRepository.save(copy));
	}

	@Transactional
	public QuestionResponse update(UUID teacherId, UUID questionId, UpdateQuestionRequest request) {
		Question question = requireOwnedQuestion(teacherId, questionId);
		requireOwnedLessonForUpdate(teacherId, question.getLesson().getId());
		if (request.version() != question.getVersion()) {
			throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The question was changed by another request.");
		}
		QuestionConfiguration configuration = merge(question, request);
		validate(configuration);
		try {
			question.replaceConfiguration(
					configuration.type(), configuration.prompt(), configuration.explanation(), configuration.options(),
					configuration.correctBoolean(), configuration.correctNumericValue(), configuration.absoluteTolerance(),
					configuration.unit(), configuration.decimalPlaces()
			);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_QUESTION_OPTION",
					"Question option does not belong to this question."
			);
		}
		return toResponse(question);
	}

	@Transactional
	public List<QuestionResponse> reorder(UUID teacherId, UUID lessonId, QuestionOrderRequest request) {
		requireOwnedLessonForUpdate(teacherId, lessonId);
		List<Question> questions = questionRepository.findByLessonIdOrderByPositionAsc(lessonId);
		if (questions.size() != request.questionIds().size()
				|| !questions.stream().map(Question::getId).collect(Collectors.toSet())
				.equals(new HashSet<>(request.questionIds()))) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_QUESTION_ORDER", "Question order is invalid.");
		}
		int maximumPosition = questions.stream().mapToInt(Question::getPosition).max().orElse(0);
		int temporaryStart = maximumPosition + questions.size() + 1;
		for (int index = 0; index < questions.size(); index++) {
			questions.get(index).moveTo(temporaryStart + index);
		}
		questionRepository.flush();
		Map<UUID, Question> questionsById = questions.stream()
				.collect(Collectors.toMap(Question::getId, Function.identity()));
		for (int index = 0; index < request.questionIds().size(); index++) {
			questionsById.get(request.questionIds().get(index)).moveTo(index + 1);
		}
		questionRepository.flush();
		return questionRepository.findByLessonIdOrderByPositionAsc(lessonId).stream().map(this::toResponse).toList();
	}

	private void validate(CreateQuestionRequest request) {
		validate(new QuestionConfiguration(
				request.prompt(), request.type(), request.explanation(), toOptionData(request.options()),
				request.correctBoolean(), request.correctNumericValue(), request.absoluteTolerance(), request.unit(),
				request.decimalPlaces()
		));
	}

	private void validate(QuestionConfiguration configuration) {
		if (configuration.type() == QuestionType.SINGLE_CHOICE) {
			long correctOptions = configuration.options().stream().filter(QuestionOptionData::correct).count();
			if (configuration.options().size() < 2 || correctOptions != 1) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_QUESTION_OPTIONS", "Single choice needs exactly one correct option.");
			}
		}
		if (configuration.type() == QuestionType.MULTIPLE_CHOICE) {
			long correctOptions = configuration.options().stream().filter(QuestionOptionData::correct).count();
			if (configuration.options().size() < 2 || correctOptions < 2) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_QUESTION_OPTIONS", "Multiple choice needs at least two correct options.");
			}
		}
		if (configuration.type() == QuestionType.TRUE_FALSE && configuration.correctBoolean() == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_TRUE_FALSE_ANSWER", "True or false questions need a correct answer.");
		}
		if (configuration.type() == QuestionType.NUMERIC && (configuration.correctNumericValue() == null
				|| configuration.absoluteTolerance() == null || configuration.absoluteTolerance().signum() < 0
				|| configuration.unit() == null || configuration.decimalPlaces() == null
				|| configuration.decimalPlaces() < 0)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_NUMERIC_CONFIGURATION", "Numeric question configuration is invalid.");
		}
	}

	private QuestionConfiguration merge(Question question, UpdateQuestionRequest request) {
		QuestionType type = request.type() == null ? question.getType() : request.type();
		boolean sameFamily = type == question.getType();
		List<QuestionOptionData> options = request.options() != null
				? toOptionData(request.options())
				: sameFamily ? question.getOptions().stream()
				.map(option -> new QuestionOptionData(option.getId(), option.getText(), option.isCorrect())).toList()
				: List.of();
		return new QuestionConfiguration(
				request.prompt() == null ? question.getPrompt() : request.prompt(),
				type,
				request.explanation() == null ? question.getExplanation() : request.explanation(),
				options,
				request.correctBoolean() != null ? request.correctBoolean() : sameFamily ? question.getCorrectBoolean() : null,
				request.correctNumericValue() != null ? request.correctNumericValue() : sameFamily ? question.getCorrectNumericValue() : null,
				request.absoluteTolerance() != null ? request.absoluteTolerance() : sameFamily ? question.getAbsoluteTolerance() : null,
				request.unit() != null ? request.unit() : sameFamily ? question.getUnit() : null,
				request.decimalPlaces() != null ? request.decimalPlaces() : sameFamily ? question.getDecimalPlaces() : null
		);
	}

	private List<QuestionOptionData> toOptionData(List<QuestionOptionRequest> options) {
		return options == null ? List.of() : options.stream()
				.map(option -> new QuestionOptionData(option.id(), option.text(), option.correct()))
				.toList();
	}

	private Question requireOwnedQuestion(UUID teacherId, UUID questionId) {
		return questionRepository.findByIdAndLessonTeacherId(questionId, teacherId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question was not found."));
	}

	private Lesson requireOwnedLessonForUpdate(UUID teacherId, UUID lessonId) {
		return lessonRepository.findByIdAndTeacherIdForUpdate(lessonId, teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson was not found."
		));
	}

	private QuestionResponse toResponse(Question question) {
		return new QuestionResponse(
				question.getId(), question.getLesson().getId(), question.getPrompt(), question.getType(), question.getExplanation(),
				question.getPosition(), !question.isActive(), question.getVersion(),
				question.getOptions().stream().map(option -> new QuestionOptionResponse(option.getId(), option.getText(), option.isCorrect())).toList(),
				question.getCorrectBoolean(), question.getCorrectNumericValue(), question.getAbsoluteTolerance(), question.getUnit(), question.getDecimalPlaces()
		);
	}

	private record QuestionConfiguration(
			String prompt,
			QuestionType type,
			String explanation,
			List<QuestionOptionData> options,
			Boolean correctBoolean,
			BigDecimal correctNumericValue,
			BigDecimal absoluteTolerance,
			NumericUnit unit,
			Integer decimalPlaces
	) {
	}
}

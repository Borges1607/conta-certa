package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.question.CreateQuestionRequest;
import com.ifsc.contacerta.dto.question.QuestionOptionRequest;
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
	public Question create(UUID teacherId, UUID lessonId, CreateQuestionRequest request) {
		Lesson lesson = lessonRepository.findByIdAndTeacherId(lessonId, teacherId).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson was not found."
		));
		validate(request);
		List<QuestionOptionData> options = request.options() == null
				? List.of()
				: request.options().stream().map(option -> new QuestionOptionData(option.text(), option.correct())).toList();
		return questionRepository.save(Question.choice(lesson, request.type(), request.prompt(), request.explanation(), options));
	}

	private void validate(CreateQuestionRequest request) {
		if (request.type() == QuestionType.SINGLE_CHOICE) {
			long correctOptions = request.options() == null ? 0 : request.options().stream().filter(QuestionOptionRequest::correct).count();
			if (request.options() == null || request.options().size() < 2 || correctOptions != 1) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_QUESTION_OPTIONS", "Single choice needs exactly one correct option.");
			}
		}
	}
}

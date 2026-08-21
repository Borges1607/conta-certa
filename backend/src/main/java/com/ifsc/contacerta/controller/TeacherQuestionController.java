package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.question.CreateQuestionRequest;
import com.ifsc.contacerta.dto.question.QuestionResponse;
import com.ifsc.contacerta.dto.question.QuestionOrderRequest;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/teacher/lessons/{lessonId}/questions")
@RequiredArgsConstructor
public class TeacherQuestionController {

	private final QuestionService questionService;

	@GetMapping
	public List<QuestionResponse> list(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID lessonId) {
		return questionService.list(currentUser.userId(), lessonId);
	}

	@PostMapping
	public QuestionResponse create(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID lessonId, @Valid @RequestBody CreateQuestionRequest request) {
		return questionService.create(currentUser.userId(), lessonId, request);
	}

	@PutMapping("/order")
	public List<QuestionResponse> reorder(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID lessonId, @Valid @RequestBody QuestionOrderRequest request) {
		return questionService.reorder(currentUser.userId(), lessonId, request);
	}

}

package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.QuestionService;
import com.ifsc.contacerta.dto.question.DuplicateQuestionRequest;
import com.ifsc.contacerta.dto.question.QuestionResponse;
import com.ifsc.contacerta.dto.question.UpdateQuestionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PatchMapping;

import java.util.UUID;

@RestController
@RequestMapping("/teacher/questions")
@RequiredArgsConstructor
public class TeacherQuestionMutationController {

	private final QuestionService questionService;

	@DeleteMapping("/{questionId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID questionId) {
		questionService.delete(currentUser.userId(), questionId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{questionId}/duplicate")
	public QuestionResponse duplicate(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID questionId, @Valid @RequestBody DuplicateQuestionRequest request) {
		return questionService.duplicate(currentUser.userId(), questionId, request);
	}

	@PatchMapping("/{questionId}")
	public QuestionResponse update(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID questionId, @Valid @RequestBody UpdateQuestionRequest request) {
		return questionService.update(currentUser.userId(), questionId, request);
	}
}

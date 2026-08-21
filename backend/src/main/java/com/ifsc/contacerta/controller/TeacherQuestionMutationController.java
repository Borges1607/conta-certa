package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

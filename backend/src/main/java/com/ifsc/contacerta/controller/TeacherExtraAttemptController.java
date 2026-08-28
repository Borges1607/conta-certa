package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.extraattempt.CreateExtraAttemptGrantRequest;
import com.ifsc.contacerta.dto.extraattempt.ExtraAttemptGrantResponse;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.ExtraAttemptGrantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TeacherExtraAttemptController {
	private final ExtraAttemptGrantService grantService;

	@PostMapping("/teacher/room-lessons/{assignmentId}/students/{studentId}/extra-attempts")
	public ExtraAttemptGrantResponse grant(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID assignmentId,
			@PathVariable UUID studentId,
			@Valid @RequestBody CreateExtraAttemptGrantRequest request
	) {
		return grantService.grant(currentUser.userId(), assignmentId, studentId, request);
	}
}

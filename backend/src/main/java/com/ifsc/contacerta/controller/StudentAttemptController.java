package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.attempt.*;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.AttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StudentAttemptController {
	private final AttemptService attemptService;
	@PostMapping("/student/room-lessons/{assignmentId}/attempts")
	public ResponseEntity<AttemptResponse> start(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID assignmentId, @RequestHeader(name = "Idempotency-Key", required = false) String key) { AttemptStartResult result = attemptService.start(user.userId(), assignmentId, key); ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status()); if (result.location() != null) response.location(result.location()); return response.body(result.body()); }
	@GetMapping("/student/attempts/{attemptId}")
	public AttemptResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID attemptId) { return attemptService.get(user.userId(), attemptId); }
	@PutMapping("/student/attempts/{attemptId}/answers/{snapshotId}")
	public AttemptAnswerReceiptResponse answer(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID attemptId, @PathVariable UUID snapshotId, @RequestBody RecordAttemptAnswerRequest request) { return attemptService.answer(user.userId(), attemptId, snapshotId, request); }
	@PostMapping("/student/attempts/{attemptId}/submit")
	public AttemptResultResponse submit(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID attemptId) { return attemptService.submit(user.userId(), attemptId); }
	@GetMapping("/student/attempts/{attemptId}/result")
	public AttemptResultResponse result(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID attemptId) { return attemptService.result(user.userId(), attemptId); }
}

package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.teacher.TeacherDashboardResponse;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.TeacherDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/teacher/dashboard")
@RequiredArgsConstructor
public class TeacherDashboardController {

	private final TeacherDashboardService service;

	@GetMapping
	public TeacherDashboardResponse get(@AuthenticationPrincipal CurrentUser currentUser) {
		return service.get(currentUser.userId());
	}
}

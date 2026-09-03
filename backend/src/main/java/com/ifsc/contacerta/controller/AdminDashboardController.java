package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.admin.AdminDashboardResponse;
import com.ifsc.contacerta.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
	private final AdminDashboardService service;

	@GetMapping
	public AdminDashboardResponse get() {
		return service.get();
	}
}

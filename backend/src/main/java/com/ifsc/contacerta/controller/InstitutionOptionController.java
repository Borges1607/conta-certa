package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.institution.InstitutionOptionResponse;
import com.ifsc.contacerta.service.InstitutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/institutions")
@RequiredArgsConstructor
public class InstitutionOptionController {

	private final InstitutionService institutionService;

	@GetMapping("/options")
	public List<InstitutionOptionResponse> listActiveOptions() {
		return institutionService.listActiveOptions();
	}
}

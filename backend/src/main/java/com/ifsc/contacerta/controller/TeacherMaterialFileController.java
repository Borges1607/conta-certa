package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.material.MaterialFileResponse;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.MaterialFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/teacher/materials/files")
@RequiredArgsConstructor
public class TeacherMaterialFileController {

	private final MaterialFileService service;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<MaterialFileResponse> upload(
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestPart(name = "file", required = false) MultipartFile file
	) {
		MaterialFileResponse response = service.upload(currentUser.userId(), file);
		return ResponseEntity.created(URI.create("/files/" + response.id())).body(response);
	}
}

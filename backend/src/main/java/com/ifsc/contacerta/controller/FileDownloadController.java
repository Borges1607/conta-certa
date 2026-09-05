package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.mapper.FileDownloadResponseMapper;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.FileDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FileDownloadController {

	private final FileDownloadService service;
	private final FileDownloadResponseMapper mapper;

	@GetMapping("/files/{fileId}/download")
	public ResponseEntity<byte[]> download(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable String fileId) {
		return mapper.toResponse(service.get(currentUser.userId(), parseFileId(fileId)));
	}

	private UUID parseFileId(String value) {
		try {
			UUID fileId = UUID.fromString(value);
			if (fileId.toString().equalsIgnoreCase(value)) {
				return fileId;
			}
		} catch (IllegalArgumentException ignored) {
			// Invalid and noncanonical UUIDs share this route's API error envelope.
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "File ID must be a valid UUID.");
	}
}

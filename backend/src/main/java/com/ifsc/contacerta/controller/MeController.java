package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.auth.ChangePasswordRequest;
import com.ifsc.contacerta.dto.auth.UserResponse;
import com.ifsc.contacerta.dto.auth.UpdateProfileRequest;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

	private final CurrentUserService currentUserService;

	@GetMapping
	public UserResponse get(@AuthenticationPrincipal CurrentUser currentUser) {
		return currentUserService.get(currentUser);
	}

	@PatchMapping
	public UserResponse updateProfile(
			@AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody UpdateProfileRequest request
	) {
		return currentUserService.updateProfile(currentUser, request);
	}

	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(
			@AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody ChangePasswordRequest request
	) {
		currentUserService.changePassword(currentUser, request);
	}
}

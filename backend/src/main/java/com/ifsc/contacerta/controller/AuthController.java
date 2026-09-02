package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.auth.AuthResponse;
import com.ifsc.contacerta.dto.auth.LoginRequest;
import com.ifsc.contacerta.dto.auth.RefreshRequest;
import com.ifsc.contacerta.dto.auth.AcceptTeacherInviteRequest;
import com.ifsc.contacerta.dto.auth.ActionTokenRequest;
import com.ifsc.contacerta.dto.auth.ForgotPasswordRequest;
import com.ifsc.contacerta.dto.auth.ResendVerificationRequest;
import com.ifsc.contacerta.dto.auth.ResetPasswordRequest;
import com.ifsc.contacerta.dto.auth.StudentRegistrationRequest;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.AuthService;
import com.ifsc.contacerta.service.AccountLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final AccountLifecycleService accountLifecycleService;

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@AuthenticationPrincipal CurrentUser currentUser) {
		authService.logout(currentUser);
	}

	@PostMapping("/student-registration")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void registerStudent(@Valid @RequestBody StudentRegistrationRequest request) { accountLifecycleService.registerStudent(request); }

	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyEmail(@Valid @RequestBody ActionTokenRequest request) { accountLifecycleService.verifyEmail(request.token()); }

	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) { accountLifecycleService.resendVerification(request.email()); }

	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) { accountLifecycleService.forgotPassword(request.email()); }

	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) { accountLifecycleService.resetPassword(request.token(), request.newPassword()); }

	@PostMapping("/accept-teacher-invite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void acceptTeacherInvite(@Valid @RequestBody AcceptTeacherInviteRequest request) { accountLifecycleService.acceptTeacherInvite(request.token(), request.password()); }
}

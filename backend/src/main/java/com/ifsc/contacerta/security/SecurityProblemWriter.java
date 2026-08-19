package com.ifsc.contacerta.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper;
	private final Clock clock;

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException exception
	) throws IOException {
		write(
				request,
				response,
				HttpStatus.UNAUTHORIZED,
				"INVALID_ACCESS_TOKEN",
				"Access token is invalid or expired."
		);
	}

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			org.springframework.security.access.AccessDeniedException exception
	) throws IOException {
		write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied.");
	}

	public void writeInvalidAccessToken(
			HttpServletRequest request,
			HttpServletResponse response
	) throws IOException {
		write(
				request,
				response,
				HttpStatus.UNAUTHORIZED,
				"INVALID_ACCESS_TOKEN",
				"Access token is invalid or expired."
		);
	}

	private void write(
			HttpServletRequest request,
			HttpServletResponse response,
			HttpStatus status,
			String code,
			String detail
	) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		problem.setProperty("timestamp", Instant.now(clock));
		problem.setProperty("traceId", traceId(request));

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), problem);
	}

	private String traceId(HttpServletRequest request) {
		String traceId = request.getHeader("X-Trace-Id");
		return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
	}
}

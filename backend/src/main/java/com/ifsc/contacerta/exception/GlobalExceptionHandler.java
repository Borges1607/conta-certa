package com.ifsc.contacerta.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.net.URI;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private final Clock clock;

	public GlobalExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ProblemDetail> handleApiException(
			ApiException exception,
			HttpServletRequest request
	) {
		return response(exception.getStatus(), exception.getCode(), exception.getMessage(), request, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
				.toList();

		return response(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"VALIDATION_ERROR",
				"One or more fields are invalid.",
				request,
				fieldErrors
		);
	}

	private ResponseEntity<ProblemDetail> response(
			HttpStatus status,
			String code,
			String detail,
			HttpServletRequest request,
			Object fieldErrors
	) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		problem.setProperty("timestamp", Instant.now(clock));
		problem.setProperty("traceId", traceId(request));
		if (fieldErrors != null) {
			problem.setProperty("fieldErrors", fieldErrors);
		}

		return ResponseEntity
				.status(status)
				.header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
				.body(problem);
	}

	private String traceId(HttpServletRequest request) {
		String traceId = request.getHeader("X-Trace-Id");
		return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
	}
}

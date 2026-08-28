package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.attempt.AttemptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class IdempotencyResponseCodec {

	private final ObjectMapper objectMapper;

	public String encode(AttemptResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Could not serialize idempotent response.", exception);
		}
	}

	public AttemptResponse decode(String storedBody) {
		try {
			return objectMapper.readValue(storedBody, AttemptResponse.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Could not deserialize idempotent response.", exception);
		}
	}
}

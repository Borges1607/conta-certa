package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.attempt.AttemptResponse;
import com.ifsc.contacerta.model.AttemptStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyResponseCodecTest {

	private final IdempotencyResponseCodec codec = new IdempotencyResponseCodec(
			new ObjectMapper()
	);

	@Test
	void deveRestaurarExatamenteOCorpoPublicoPersistido() {
		AttemptResponse response = new AttemptResponse(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"Frações",
				AttemptStatus.IN_PROGRESS,
				Instant.parse("2026-08-28T12:00:00Z"),
				Instant.parse("2026-08-28T12:30:00Z"),
				30,
				List.of(),
				List.of(),
				60
		);

		String storedBody = codec.encode(response);

		assertThat(codec.decode(storedBody)).isEqualTo(response);
		assertThat(storedBody).contains(response.attemptId().toString());
	}
}

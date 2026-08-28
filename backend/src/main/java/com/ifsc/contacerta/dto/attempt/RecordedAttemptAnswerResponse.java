package com.ifsc.contacerta.dto.attempt;

import java.time.Instant;
import java.util.UUID;

public record RecordedAttemptAnswerResponse(
		UUID questionSnapshotId,
		Instant answeredAt,
		AttemptAnswerValueResponse answer
) {
}

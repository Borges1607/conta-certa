package com.ifsc.contacerta.dto.attempt;

import java.time.Instant;
import java.util.UUID;

public record AttemptAnswerReceiptResponse(UUID questionSnapshotId, Instant answeredAt, boolean correct) {
}

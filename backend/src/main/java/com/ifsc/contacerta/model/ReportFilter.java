package com.ifsc.contacerta.model;

import java.time.Instant;
import java.util.UUID;

public record ReportFilter(
		UUID roomId,
		UUID lessonId,
		Instant from,
		Instant to
) {

	public boolean allTime() {
		return from == null;
	}
}

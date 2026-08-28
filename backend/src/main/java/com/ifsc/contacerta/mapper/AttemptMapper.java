package com.ifsc.contacerta.mapper;
import com.ifsc.contacerta.dto.attempt.*; import com.ifsc.contacerta.entity.*; import org.springframework.stereotype.Component; import java.time.Instant;
@Component
public class AttemptMapper {
	public AttemptResponse toPublicResponse(Attempt attempt, Instant now) { return new AttemptResponse(attempt.getId(), attempt.getAssignment().getId(), attempt.getSequence(), attempt.getStatus(), attempt.getStartedAt(), attempt.getExpiresAt(), now, attempt.getSnapshots().stream().map(this::question).toList(), attempt.getVersion()); }
	private AttemptQuestionResponse question(AttemptQuestionSnapshot q) { return new AttemptQuestionResponse(q.getId(), q.getType(), q.getPrompt(), q.getPosition(), q.getUnit(), q.getDecimalPlaces(), q.getOptions().stream().map(o -> new AttemptOptionResponse(o.getId(), o.getText(), o.getPosition())).toList(), null); }
}

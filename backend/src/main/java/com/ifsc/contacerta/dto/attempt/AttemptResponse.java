package com.ifsc.contacerta.dto.attempt;
import com.ifsc.contacerta.model.AttemptStatus; import java.time.Instant; import java.util.List; import java.util.UUID;
public record AttemptResponse(UUID id, UUID assignmentId, int sequence, AttemptStatus status, Instant startedAt, Instant expiresAt, Instant serverTime, List<AttemptQuestionResponse> questions, long version) {}

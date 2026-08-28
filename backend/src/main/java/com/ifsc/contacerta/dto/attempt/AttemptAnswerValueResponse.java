package com.ifsc.contacerta.dto.attempt;
import java.time.Instant; import java.util.Set; import java.util.UUID;
public record AttemptAnswerValueResponse(Set<UUID> selectedOptionIds, Boolean booleanValue, String numericValue, Instant answeredAt) {}

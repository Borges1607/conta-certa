package com.ifsc.contacerta.dto.attempt;

import java.util.List;
import java.util.UUID;

public record RecordAttemptAnswerRequest(List<UUID> selectedOptionIds, Boolean booleanValue, String numericValue) {}

package com.ifsc.contacerta.dto.question;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record QuestionOrderRequest(@NotEmpty List<UUID> questionIds) {
}

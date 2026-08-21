package com.ifsc.contacerta.dto.question;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DuplicateQuestionRequest(@NotNull UUID targetLessonId) {
}

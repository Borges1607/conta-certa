package com.ifsc.contacerta.dto.question;

import java.util.UUID;

public record QuestionOptionResponse(UUID id, String text, boolean correct) {
}

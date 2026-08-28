package com.ifsc.contacerta.dto.attempt;
import java.time.Instant;
public record AttemptAnswerReceiptResponse(boolean correct, Instant answeredAt) {}

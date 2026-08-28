package com.ifsc.contacerta.dto.extraattempt;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateExtraAttemptGrantRequest(@Min(1) @Max(100) int quantity) {}

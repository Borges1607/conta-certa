package com.ifsc.contacerta.dto.extraattempt;

import java.util.UUID;

public record ExtraAttemptGrantResponse(UUID id, int grantedTotal, long attemptsUsed, Long attemptsAvailable) {}

package com.ifsc.contacerta.dto.studentlesson;

import com.ifsc.contacerta.model.AttemptStatus;

import java.time.Instant;
import java.util.UUID;

public record AttemptHistoryResponse(UUID id, int sequence, AttemptStatus status, Integer scorePercent, Boolean passed, Instant startedAt, Instant submittedAt) {}

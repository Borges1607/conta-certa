package com.ifsc.contacerta.dto.report;

import java.time.LocalDate;

public record ReportAttemptSeriesItemResponse(LocalDate date, long attemptCount) { }

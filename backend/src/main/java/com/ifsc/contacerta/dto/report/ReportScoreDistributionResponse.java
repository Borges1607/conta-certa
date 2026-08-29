package com.ifsc.contacerta.dto.report;

public record ReportScoreDistributionResponse(
		long score0To49,
		long score50To69,
		long score70To89,
		long score90To100
) { }

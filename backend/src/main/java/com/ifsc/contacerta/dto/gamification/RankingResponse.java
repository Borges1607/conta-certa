package com.ifsc.contacerta.dto.gamification;

import java.util.List;

public record RankingResponse(
		List<RankingEntryResponse> content,
		RankingEntryResponse self,
		int page,
		int size,
		long totalElements,
		int totalPages
) {}

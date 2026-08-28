package com.ifsc.contacerta.dto.media;

import java.util.List;

public record MediaCollectionResponse<T>(List<T> items, long viewedCount, long totalCount) {
}

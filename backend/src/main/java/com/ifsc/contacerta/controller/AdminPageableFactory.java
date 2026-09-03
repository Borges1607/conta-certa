package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AdminPageableFactory {

	public Pageable create(int page, int size, String sort, Set<String> allowedFields, String sortErrorCode) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_PAGE", "Pagination values are invalid.");
		}
		String[] parts = (sort == null || sort.isBlank() ? "createdAt,desc" : sort).split(",", -1);
		if (parts.length > 2 || parts[0].isBlank() || !allowedFields.contains(parts[0])) {
			throw invalidSort(sortErrorCode);
		}
		Sort.Direction direction = parts.length == 1
				? Sort.Direction.ASC
				: Sort.Direction.fromOptionalString(parts[1]).orElseThrow(() -> invalidSort(sortErrorCode));
		return PageRequest.of(page, size, Sort.by(direction, parts[0]));
	}

	private ApiException invalidSort(String code) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, "Sort is invalid.");
	}
}

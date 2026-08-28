package com.ifsc.contacerta.service;

import com.ifsc.contacerta.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ExternalUrlValidator {

	public String requireHttps(String value, String field) {
		try {
			URI uri = URI.create(value);
			if (!"https".equalsIgnoreCase(uri.getScheme())
					|| uri.getHost() == null
					|| uri.getHost().isBlank()
					|| uri.getUserInfo() != null) {
				throw invalid(field);
			}
			return uri.toString();
		} catch (IllegalArgumentException exception) {
			throw invalid(field);
		}
	}

	private ApiException invalid(String field) {
		return new ApiException(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"INVALID_MEDIA",
				"A valid HTTPS URL is required for " + field + "."
		);
	}
}

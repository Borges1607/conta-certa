package com.ifsc.contacerta.service;

import com.ifsc.contacerta.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PasswordPolicy {

	private static final int MIN_LENGTH = 8;
	private static final int MAX_LENGTH = 72;

	public void validate(String password) {
		if (password == null
				|| password.length() < MIN_LENGTH
				|| password.length() > MAX_LENGTH
				|| password.chars().noneMatch(Character::isLetter)
				|| password.chars().noneMatch(Character::isDigit)) {
			throw new ApiException(
					HttpStatus.UNPROCESSABLE_CONTENT,
					"INVALID_PASSWORD",
					"Password must contain 8 to 72 characters, including at least one letter and one number."
			);
		}
	}
}

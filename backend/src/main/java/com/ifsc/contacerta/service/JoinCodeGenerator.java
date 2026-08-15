package com.ifsc.contacerta.service;

import com.ifsc.contacerta.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

@Component
@RequiredArgsConstructor
public class JoinCodeGenerator {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 6;

	private final RoomRepository roomRepository;
	private final RandomGenerator random;

	public String generateUnique() {
		String code;
		do {
			code = generate();
		} while (roomRepository.existsByJoinCode(code));
		return code;
	}

	private String generate() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int index = 0; index < CODE_LENGTH; index++) {
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}
}

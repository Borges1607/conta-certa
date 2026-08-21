package com.ifsc.contacerta.service;

import com.ifsc.contacerta.repository.RoomRepository;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JoinCodeGeneratorTest {

	@Test
	void deveTentarNovamenteQuandoCodigoColidir() {
		RoomRepository roomRepository = mock(RoomRepository.class);
		RandomGenerator random = mock(RandomGenerator.class);
		JoinCodeHasher hasher = new JoinCodeHasher();
		when(random.nextInt(32)).thenReturn(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
		when(roomRepository.existsByJoinCodeHash(hasher.hash("ABCDEF"))).thenReturn(true);
		when(roomRepository.existsByJoinCodeHash(hasher.hash("GHJKLM"))).thenReturn(false);
		JoinCodeGenerator generator = new JoinCodeGenerator(roomRepository, random, hasher);

		String code = generator.generateUnique();

		assertThat(code).isEqualTo("GHJKLM");
	}
}

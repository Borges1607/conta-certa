package com.ifsc.contacerta.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Configuration
public class RandomConfig {

	@Bean
	RandomGenerator secureRandomGenerator() {
		return new SecureRandom();
	}
}

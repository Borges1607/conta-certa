package com.ifsc.contacerta.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Configuration
@EnableConfigurationProperties(AttemptProperties.class)
@EnableScheduling
public class AttemptConfig {

	@Bean
	RandomGenerator attemptRandomGenerator() {
		return new SecureRandom();
	}
}

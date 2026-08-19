package com.ifsc.contacerta.service;

import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(InitialAdminProperties.class)
public class InitialAdminInitializer implements ApplicationRunner {

	private final InitialAdminProperties properties;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;

	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		run();
	}

	@Transactional
	public void run() {
		boolean namePresent = hasText(properties.name());
		boolean emailPresent = hasText(properties.email());
		boolean passwordPresent = hasText(properties.password());
		int configuredValues = (namePresent ? 1 : 0) + (emailPresent ? 1 : 0) + (passwordPresent ? 1 : 0);

		if (configuredValues == 0) {
			return;
		}
		if (configuredValues != 3) {
			throw new IllegalStateException("Initial administrator configuration must be complete.");
		}

		String normalizedEmail = properties.email().trim().toLowerCase(Locale.ROOT);
		passwordPolicy.validate(properties.password());
		if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			return;
		}

		User admin = new User(
				Role.ADMIN,
				AccountStatus.ACTIVE,
				properties.name().trim(),
				normalizedEmail,
				null,
				null
		);
		admin.initializePassword(passwordEncoder.encode(properties.password()), true);
		userRepository.save(admin);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}

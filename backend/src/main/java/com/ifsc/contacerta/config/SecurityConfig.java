package com.ifsc.contacerta.config;

import com.ifsc.contacerta.security.JwtAuthenticationFilter;
import com.ifsc.contacerta.security.SecurityProblemWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final SecurityProblemWriter problemWriter;
	private final SecurityProperties properties;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh").permitAll()
						.requestMatchers(HttpMethod.GET, "/institutions/options").permitAll()
						.requestMatchers(
								HttpMethod.GET,
								"/student/rooms/*/ranking",
								"/student/rooms/*/achievements"
						).hasRole("STUDENT")
						.anyRequest().authenticated()
				)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(problemWriter)
						.accessDeniedHandler(problemWriter)
				)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(properties.password().bcryptStrength());
	}
}

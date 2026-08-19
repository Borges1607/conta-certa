package com.ifsc.contacerta.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(TestRsaKeyConfiguration.class)
public abstract class PostgresIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES;

	static {
		POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
		POSTGRES.start();
	}
}

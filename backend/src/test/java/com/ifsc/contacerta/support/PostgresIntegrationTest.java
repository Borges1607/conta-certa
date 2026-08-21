package com.ifsc.contacerta.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@SpringBootTest
@Import(TestRsaKeyConfiguration.class)
public abstract class PostgresIntegrationTest {

	@Autowired
	private DataSource dataSource;

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES;

	static {
		POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
		POSTGRES.start();
	}

	@BeforeEach
	void clearDatabase() throws Exception {
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			connection.setAutoCommit(true);
			statement.execute("TRUNCATE TABLE room_memberships, room_topics, rooms, auth_sessions, refresh_tokens, users, institutions CASCADE");
		}
	}
}

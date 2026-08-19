package com.ifsc.contacerta.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseIsolationTest extends PostgresIntegrationTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void deveUsarPostgresDoTestcontainers() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL())
					.contains("jdbc:postgresql://localhost:")
					.doesNotContain(":5432/contacerta");
		}
	}
}

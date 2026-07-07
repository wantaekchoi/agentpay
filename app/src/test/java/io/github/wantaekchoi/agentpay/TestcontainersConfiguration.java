package io.github.wantaekchoi.agentpay;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared Testcontainers Postgres bean for integration tests that need a real
 * database (Flyway + JPA). Imported by both {@link AgentpayApplicationTests}
 * and identity persistence tests instead of each declaring its own container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17");
    }
}

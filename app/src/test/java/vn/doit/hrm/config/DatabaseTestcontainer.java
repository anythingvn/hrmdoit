package vn.doit.hrm.config;

import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared PostgreSQL for every integration test.
 *
 * <p>The container is a JVM-wide singleton: it is started once, on first use, and is never
 * registered as a Spring bean. Spring's test-context cache may evict and close a context at any
 * time (several ITs bring their own {@code @TestConfiguration}, e.g. a fixed {@code Clock});
 * if the container were a bean ({@code @ServiceConnection}), closing that context would stop the
 * container and every later context would fail with connection errors. Here no context owns it:
 * the datasource is wired through plain properties, and the container lives until the JVM exits
 * (Testcontainers' Ryuk removes it).
 */
@TestConfiguration(proxyBeanMethods = false)
public class DatabaseTestcontainer {

    private static final class Holder {

        private static final PostgreSQLContainer CONTAINER = start();

        private static PostgreSQLContainer start() {
            PostgreSQLContainer container = new PostgreSQLContainer("postgres:16.15-bookworm")
                .withDatabaseName("hrm")
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(DatabaseTestcontainer.class)))
                .withReuse(true);
            container.start();
            return container;
        }
    }

    /** The shared container, started on first call and kept running for the rest of the JVM. */
    public static PostgreSQLContainer container() {
        return Holder.CONTAINER;
    }

    @Bean
    DynamicPropertyRegistrar databaseContainerProperties() {
        return registry -> {
            PostgreSQLContainer container = container();
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        };
    }
}

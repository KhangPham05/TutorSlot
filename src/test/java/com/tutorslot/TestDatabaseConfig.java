package com.tutorslot;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Test-only: every Spring test context starts from a fresh database, so tests that book slots
// can't affect each other across contexts. Replaces Flyway's normal startup step (migrate only)
// with clean (wipe the test schema) then migrate (rebuild it from the app's own migration files).
// It lives under src/test, so component scanning finds it in tests and the real app never sees
// it; the app only ever migrates.
@Configuration(proxyBeanMethods = false)
class TestDatabaseConfig {

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}

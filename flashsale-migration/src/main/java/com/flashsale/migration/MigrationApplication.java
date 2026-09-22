package com.flashsale.migration;

import org.flywaydb.core.Flyway;

public final class MigrationApplication {
    private MigrationApplication() {}

    public static void main(String[] args) {
        String url = env("FLYWAY_URL", "jdbc:postgresql://localhost:5432/flashsale");
        String user = env("FLYWAY_USER", "flashsale");
        String password = env("FLYWAY_PASSWORD", "flashsale");
        Flyway.configure()
                .dataSource(url, user, password)
                .locations("filesystem:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}

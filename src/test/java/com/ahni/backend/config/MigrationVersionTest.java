package com.ahni.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class MigrationVersionTest {
    @Test
    void versionedMigrationNumbersAreUnique() throws Exception {
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            var migrations = files.map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("V") && name.endsWith(".sql"))
                .collect(Collectors.groupingBy(name -> name.substring(1, name.indexOf("__"))));

            migrations.forEach((version, names) -> assertThat(names)
                .as("Flyway version %s must identify one migration: %s", version, names)
                .hasSize(1));
        }
    }
}

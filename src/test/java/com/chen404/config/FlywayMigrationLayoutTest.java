package com.chen404.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 Flyway 迁移目录已接管当前数据库交付基线。
 */
class FlywayMigrationLayoutTest {

    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    private static final List<String> EXPECTED_MIGRATIONS = List.of(
            "V2026032601__baseline_schema.sql",
            "V2026032801__interaction_like_tables.sql",
            "V2026040201__article_cover_file_id.sql",
            "V2026042501__create_user_trust_request.sql",
            "V2026042502__drop_trust_request_attachment_urls.sql"
    );

    @Test
    void shouldProvideOrderedFlywayMigrationsForCurrentSchema() throws IOException {
        assertTrue(Files.isDirectory(MIGRATION_DIR), "Flyway 迁移目录缺失");

        List<String> migrationFiles = Files.list(MIGRATION_DIR)
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());

        assertEquals(EXPECTED_MIGRATIONS, migrationFiles, "Flyway 迁移文件顺序或命名不符合预期");
    }
}

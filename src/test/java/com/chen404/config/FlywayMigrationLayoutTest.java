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
            "V2026042502__drop_trust_request_attachment_urls.sql",
            "V2026050602__site_config_runtime_defaults.sql",
            "V2026050603__remove_runtime_site_config_keys.sql",
            "V2026051001__ai_chat_and_article_chunk.sql",
            "V2026051301__create_travel_memory_tables.sql",
            "V2026052101__create_file_reference_table.sql",
            "V2026052401__add_travel_memory_visited_end_at.sql",
            "V2026052501__ai_admin_config.sql",
            "V2026052601__simplify_file_reference_and_drop_unused_tables.sql",
            "V2026052701__create_music_radio_tables.sql"
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

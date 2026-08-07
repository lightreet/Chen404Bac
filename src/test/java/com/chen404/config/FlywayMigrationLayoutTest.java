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
    private static final Path MULTI_USER_MIGRATION = MIGRATION_DIR.resolve(
            "V2026072701__multi_user_creator_platform.sql");
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
            "V2026052701__create_music_radio_tables.sql",
            "V2026060501__add_travel_memory_stop_groups.sql",
            "V2026061202__normalize_legacy_icon_names.sql",
            "V2026062201__add_travel_memory_visibility.sql",
            "V2026072701__multi_user_creator_platform.sql",
            "V2026072702__harden_multi_user_security.sql",
            "V2026072901__create_reader_library.sql",
            "V2026073001__add_reader_book_visibility.sql",
            "V2026073002__add_reader_book_custom_cover.sql",
            "V2026073003__default_reader_book_visibility_public.sql",
            "V2026080302__allow_reader_book_reimport_after_delete.sql"
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

    @Test
    void shouldKeepLegacyMusicUrlBackfillIndependentOfColumnCollation() throws IOException {
        String migrationSql = Files.readString(MULTI_USER_MIGRATION);

        assertTrue(migrationSql.contains(
                        "BINARY `audio_url_file`.`file_url` = BINARY `track`.`audio_url`"),
                "音乐音频 URL 回填必须使用二进制比较，避免不同 utf8mb4 排序规则导致迁移失败");
        assertTrue(migrationSql.contains(
                        "BINARY `cover_url_file`.`file_url` = BINARY `track`.`cover_url`"),
                "音乐封面 URL 回填必须使用二进制比较，避免不同 utf8mb4 排序规则导致迁移失败");
    }
}

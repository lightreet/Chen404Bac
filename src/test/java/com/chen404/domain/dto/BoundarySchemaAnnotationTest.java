package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundarySchemaAnnotationTest {

    @Test
    void boundaryCommandsAndVosShouldHaveSchemaDescriptions() {
        List<Class<?>> classes = List.of(
                CreateArticleCommand.class,
                UpdateArticleCommand.class,
                CreateCategoryCommand.class,
                UpdateCategoryCommand.class,
                ArticleListItemVO.class,
                ArticleDetailVO.class,
                CategoryVO.class,
                TagVO.class,
                BannerVO.class,
                CommentVO.class,
                EmojiPackVO.class,
                EmojiItemVO.class,
                EmojiImportResultDTO.class,
                RecentCommentVO.class,
                ArticleNeighborsVO.class,
                FavoriteToggleResultDTO.class,
                SendCodeResultDTO.class,
                SiteStatsVO.class,
                HomeDataVO.class,
                UploadFileVO.class,
                UserProfileVO.class,
                LoginResultDTO.class
        );

        for (Class<?> clazz : classes) {
            assertClassSchema(clazz);
            for (Field field : clazz.getDeclaredFields()) {
                assertFieldSchema(clazz, field);
            }
        }
    }

    private void assertClassSchema(Class<?> clazz) {
        Schema schema = clazz.getAnnotation(Schema.class);
        assertNotNull(schema, () -> clazz.getSimpleName() + " 缺少 @Schema");
        assertFalse(schema.description().isBlank(), () -> clazz.getSimpleName() + " 的 @Schema.description 不能为空");
    }

    private void assertFieldSchema(Class<?> clazz, Field field) {
        Schema schema = field.getAnnotation(Schema.class);
        assertNotNull(schema, () -> clazz.getSimpleName() + "." + field.getName() + " 缺少 @Schema");
        assertTrue(!schema.description().isBlank(), () -> clazz.getSimpleName() + "." + field.getName() + " 的描述不能为空");
    }
}
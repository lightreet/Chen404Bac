package com.chen404.domain.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoNamingConventionTest {

    private static final List<Class<?>> COMMAND_TYPES = List.of(
            CreateArticleCommand.class,
            UpdateArticleCommand.class,
            CreateCategoryCommand.class,
            UpdateCategoryCommand.class,
            CreateTravelMemoryCommand.class,
            UpdateTravelMemoryCommand.class,
            TravelMemoryEntryUpsertCommand.class
    );

    private static final List<Class<?>> VO_TYPES = List.of(
            ArticleDetailVO.class,
            ArticleListItemVO.class,
            CategoryVO.class,
            TagVO.class,
            UserProfileVO.class,
            TravelMemoryLocationDetailVO.class,
            TravelMemoryLocationListItemVO.class
    );

    private static final List<Class<?>> LEGACY_DTO_TYPES = List.of(
            LoginDTO.class,
            RegisterDTO.class,
            SendCodeDTO.class,
            RefreshTokenDTO.class,
            ChangePasswordDTO.class,
            UpdateProfileDTO.class,
            CreateCommentDTO.class,
            ReviewCommentDTO.class,
            ReviewTrustRequestDTO.class,
            UpdateTrustLevelDTO.class,
            SingleFileUploadDTO.class,
            MultiFileUploadDTO.class
    );

    @Test
    void requestCommandTypesShouldUseCommandSuffix() {
        for (Class<?> type : COMMAND_TYPES) {
            assertTrue(type.getSimpleName().endsWith("Command"),
                    () -> type.getSimpleName() + " 应以 Command 结尾");
        }
    }

    @Test
    void responseViewTypesShouldUseVoSuffix() {
        for (Class<?> type : VO_TYPES) {
            assertTrue(type.getSimpleName().endsWith("VO"),
                    () -> type.getSimpleName() + " 应以 VO 结尾");
        }
    }

    @Test
    void legacyDtoNamesShouldBeExplicitlyWhitelistedBeforeFutureRename() {
        for (Class<?> type : LEGACY_DTO_TYPES) {
            assertTrue(type.getSimpleName().endsWith("DTO"),
                    () -> type.getSimpleName() + " 是兼容保留 DTO，未来重命名前需同步前端契约");
        }
    }
}

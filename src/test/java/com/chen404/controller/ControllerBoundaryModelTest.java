package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.ArticleDetailVO;
import com.chen404.domain.dto.ArticleListItemVO;
import com.chen404.domain.dto.ArticleNeighborsVO;
import com.chen404.domain.dto.AdminFileStatsVO;
import com.chen404.domain.dto.CategoryVO;
import com.chen404.domain.dto.CommentVO;
import com.chen404.domain.dto.CreateArticleCommand;
import com.chen404.domain.dto.CreateCategoryCommand;
import com.chen404.domain.dto.EmojiImportResultDTO;
import com.chen404.domain.dto.EmojiItemVO;
import com.chen404.domain.dto.EmojiPackVO;
import com.chen404.domain.dto.FavoriteToggleResultDTO;
import com.chen404.domain.dto.HomeDataVO;
import com.chen404.domain.dto.LoginResultDTO;
import com.chen404.domain.dto.RecentCommentVO;
import com.chen404.domain.dto.SendCodeResultDTO;
import com.chen404.domain.dto.SiteStatsVO;
import com.chen404.domain.dto.TagVO;
import com.chen404.domain.dto.TravelMemoryLocationDetailVO;
import com.chen404.domain.dto.TravelMemoryLocationListItemVO;
import com.chen404.domain.dto.UpdateArticleCommand;
import com.chen404.domain.dto.UpdateCategoryCommand;
import com.chen404.domain.dto.UpdateTravelMemoryCommand;
import com.chen404.domain.dto.UserProfileVO;
import com.chen404.domain.dto.CreateTravelMemoryCommand;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.Category;
import com.chen404.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerBoundaryModelTest {

    @Test
    void articleControllerShouldUseCommandsAndVosAtBoundary() throws Exception {
        Method createMethod = ArticleController.class.getMethod("createArticle", CreateArticleCommand.class, AuthenticatedUser.class);
        assertRequestBodyType(createMethod, CreateArticleCommand.class);
        assertReturnTypeContains(createMethod, Result.class.getName() + "<com.chen404.domain.dto.ArticleDetailVO>");

        Method updateMethod = ArticleController.class.getMethod(
                "updateArticle",
                Long.class,
                UpdateArticleCommand.class,
                AuthenticatedUser.class
        );
        assertRequestBodyType(updateMethod, UpdateArticleCommand.class);
        assertReturnTypeContains(updateMethod, Result.class.getName() + "<com.chen404.domain.dto.ArticleDetailVO>");

        Method listMethod = ArticleController.class.getMethod(
                "getArticles",
                Integer.class,
                Integer.class,
                Integer.class,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                AuthenticatedUser.class
        );
        assertReturnTypeContains(listMethod, ArticleListItemVO.class.getName());
    }

    @Test
    void categoryControllerShouldUseCommandsAndVosAtBoundary() throws Exception {
        Method createMethod = CategoryController.class.getMethod("createCategory", CreateCategoryCommand.class);
        assertRequestBodyType(createMethod, CreateCategoryCommand.class);
        assertReturnTypeContains(createMethod, Result.class.getName() + "<com.chen404.domain.dto.CategoryVO>");

        Method updateMethod = CategoryController.class.getMethod("updateCategory", Long.class, UpdateCategoryCommand.class);
        assertRequestBodyType(updateMethod, UpdateCategoryCommand.class);
        assertReturnTypeContains(updateMethod, Result.class.getName() + "<com.chen404.domain.dto.CategoryVO>");

        Method listMethod = CategoryController.class.getMethod("getCategories", boolean.class);
        assertReturnTypeContains(listMethod, CategoryVO.class.getName());

        Method adminListMethod = AdminCategoryController.class.getMethod("pageCategories", Integer.class, Integer.class);
        assertReturnTypeContains(adminListMethod, CategoryVO.class.getName());
    }

    @Test
    void travelMemoryControllerShouldUseCommandsAndVosAtBoundary() throws Exception {
        Method listMethod = TravelMemoryController.class.getMethod("listVisibleMemories", AuthenticatedUser.class);
        assertReturnTypeContains(listMethod, TravelMemoryLocationListItemVO.class.getName());

        Method detailMethod = TravelMemoryController.class.getMethod("getVisibleMemoryDetail", Long.class, AuthenticatedUser.class);
        assertReturnTypeContains(detailMethod, Result.class.getName() + "<com.chen404.domain.dto.TravelMemoryLocationDetailVO>");

        Method adminListMethod = TravelMemoryController.class.getMethod("listAdminMemories");
        assertReturnTypeContains(adminListMethod, TravelMemoryLocationDetailVO.class.getName());

        Method adminDetailMethod = TravelMemoryController.class.getMethod(
                "getAdminMemoryDetail",
                Long.class,
                AuthenticatedUser.class
        );
        assertReturnTypeContains(adminDetailMethod, Result.class.getName() + "<com.chen404.domain.dto.TravelMemoryLocationDetailVO>");

        Method createMethod = TravelMemoryController.class.getMethod(
                "createTravelMemory",
                CreateTravelMemoryCommand.class,
                AuthenticatedUser.class
        );
        assertRequestBodyType(createMethod, CreateTravelMemoryCommand.class);
        assertReturnTypeContains(createMethod, Result.class.getName() + "<com.chen404.domain.dto.TravelMemoryLocationDetailVO>");

        Method updateMethod = TravelMemoryController.class.getMethod(
                "updateTravelMemory",
                Long.class,
                UpdateTravelMemoryCommand.class,
                AuthenticatedUser.class
        );
        assertRequestBodyType(updateMethod, UpdateTravelMemoryCommand.class);
        assertReturnTypeContains(updateMethod, Result.class.getName() + "<com.chen404.domain.dto.TravelMemoryLocationDetailVO>");
    }

    @Test
    void controllerBoundaryShouldNotFallBackToEntities() throws Exception {
        Method articleCreateMethod = ArticleController.class.getMethod("createArticle", CreateArticleCommand.class, AuthenticatedUser.class);
        assertRequestBodyIsNotEntity(articleCreateMethod, Article.class);

        Method categoryCreateMethod = CategoryController.class.getMethod("createCategory", CreateCategoryCommand.class);
        assertRequestBodyIsNotEntity(categoryCreateMethod, Category.class);
    }

    @Test
    void tagAndHomeControllerShouldExposeVosInsteadOfEntitiesOrMaps() throws Exception {
        Method getTagsMethod = TagController.class.getMethod("getTags", boolean.class);
        assertReturnTypeContains(getTagsMethod, TagVO.class.getName());

        Method getTagByIdMethod = TagController.class.getMethod("getTagById", String.class);
        assertReturnTypeContains(getTagByIdMethod, Result.class.getName() + "<com.chen404.domain.dto.TagVO>");

        Method getHomeDataMethod = HomeController.class.getMethod("getHomeData", AuthenticatedUser.class);
        assertReturnTypeContains(getHomeDataMethod, HomeDataVO.class.getName());

        Method getSiteStatsMethod = HomeController.class.getMethod("getSiteStats");
        assertReturnTypeContains(getSiteStatsMethod, SiteStatsVO.class.getName());
        assertTrue(
                !getHomeDataMethod.getGenericReturnType().getTypeName().contains("Map<java.lang.String, java.lang.Object>"),
                "首页聚合接口不应继续暴露 Map<String, Object>"
        );
    }

    @Test
    void commentAuthAdminAndSiteControllersShouldExposeVos() throws Exception {
        Method getCommentsMethod = CommentController.class.getMethod(
                "getComments",
                Long.class,
                Integer.class,
                Integer.class,
                AuthenticatedUser.class
        );
        assertReturnTypeContains(getCommentsMethod, CommentVO.class.getName());

        Method createCommentMethod = CommentController.class.getMethod(
                "createComment",
                com.chen404.domain.dto.CreateCommentDTO.class,
                AuthenticatedUser.class,
                jakarta.servlet.http.HttpServletRequest.class
        );
        assertReturnTypeContains(createCommentMethod, Result.class.getName() + "<com.chen404.domain.dto.CommentVO>");

        Method recentCommentsMethod = CommentController.class.getMethod("getRecentComments", Integer.class);
        assertReturnTypeContains(recentCommentsMethod, RecentCommentVO.class.getName());

        Method registerMethod = AuthController.class.getMethod("register", com.chen404.domain.dto.RegisterDTO.class);
        assertReturnTypeContains(registerMethod, UserProfileVO.class.getName());

        Method getUserInfoMethod = AuthController.class.getMethod("getUserInfo", AuthenticatedUser.class);
        assertReturnTypeContains(getUserInfoMethod, UserProfileVO.class.getName());

        Method loginMethod = AuthController.class.getMethod("login", com.chen404.domain.dto.LoginDTO.class, jakarta.servlet.http.HttpServletRequest.class);
        assertReturnTypeContains(loginMethod, LoginResultDTO.class.getName());

        Method sendCodeMethod = AuthController.class.getMethod("sendCode", com.chen404.domain.dto.SendCodeDTO.class);
        assertReturnTypeContains(sendCodeMethod, SendCodeResultDTO.class.getName());

        Method updateTrustLevelMethod = AdminUserController.class.getMethod("updateTrustLevel", Long.class, com.chen404.domain.dto.UpdateTrustLevelDTO.class);
        assertReturnTypeContains(updateTrustLevelMethod, UserProfileVO.class.getName());

        Method getBannersMethod = SiteController.class.getMethod("getBanners", Integer.class);
        assertReturnTypeContains(getBannersMethod, "com.chen404.domain.dto.BannerVO");
    }

    @Test
    void emojiUploadAndArticleAuxiliaryControllersShouldExposeExplicitModels() throws Exception {
        Method packsMethod = EmojiController.class.getMethod("packs");
        assertReturnTypeContains(packsMethod, EmojiPackVO.class.getName());

        Method itemsMethod = EmojiController.class.getMethod(
                "items",
                String.class,
                String.class,
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class
        );
        assertReturnTypeContains(itemsMethod, EmojiItemVO.class.getName());

        Method adminPacksMethod = EmojiController.class.getMethod("listPacks", Integer.class, Integer.class);
        assertReturnTypeContains(adminPacksMethod, EmojiPackVO.class.getName());

        Method upsertPackMethod = EmojiController.class.getMethod("upsertPack", com.chen404.domain.dto.EmojiPackUpsertDTO.class);
        assertReturnTypeContains(upsertPackMethod, EmojiPackVO.class.getName());

        Method adminItemsMethod = EmojiController.class.getMethod("listItems", Integer.class, Integer.class, String.class);
        assertReturnTypeContains(adminItemsMethod, EmojiItemVO.class.getName());

        Method upsertItemMethod = EmojiController.class.getMethod("upsertItem", com.chen404.domain.dto.EmojiItemUpsertDTO.class);
        assertReturnTypeContains(upsertItemMethod, EmojiItemVO.class.getName());

        Method importMethod = EmojiController.class.getMethod("importZip", org.springframework.web.multipart.MultipartFile.class);
        assertReturnTypeContains(importMethod, EmojiImportResultDTO.class.getName());

        Method uploadImageMethod = UploadController.class.getMethod(
                "uploadImage",
                com.chen404.domain.dto.SingleFileUploadDTO.class,
                AuthenticatedUser.class
        );
        assertReturnTypeContains(uploadImageMethod, "com.chen404.domain.dto.UploadFileVO");

        Method uploadImagesMethod = UploadController.class.getMethod(
                "uploadImages",
                com.chen404.domain.dto.MultiFileUploadDTO.class,
                AuthenticatedUser.class
        );
        assertReturnTypeContains(uploadImagesMethod, "com.chen404.domain.dto.UploadFileVO");

        Method neighborsMethod = ArticleController.class.getMethod("getArticleNeighbors", Long.class, AuthenticatedUser.class);
        assertReturnTypeContains(neighborsMethod, ArticleNeighborsVO.class.getName());

        Method favoriteMethod = ArticleController.class.getMethod("toggleFavorite", Long.class, AuthenticatedUser.class);
        assertReturnTypeContains(favoriteMethod, FavoriteToggleResultDTO.class.getName());

        Method fileStatsMethod = AdminFileController.class.getMethod("getAdminFileStats");
        assertReturnTypeContains(fileStatsMethod, Result.class.getName() + "<com.chen404.domain.dto.AdminFileStatsVO>");
    }

    private void assertRequestBodyType(Method method, Class<?> expectedType) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                assertEquals(expectedType, parameter.getType());
                return;
            }
        }
        throw new AssertionError(method.getName() + " 缺少 @RequestBody 参数");
    }

    private void assertRequestBodyIsNotEntity(Method method, Class<?> entityType) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                assertNotEquals(entityType, parameter.getType());
                return;
            }
        }
        throw new AssertionError(method.getName() + " 缺少 @RequestBody 参数");
    }

    private void assertReturnTypeContains(Method method, String expectedTypeName) {
        assertTrue(
                method.getGenericReturnType().getTypeName().contains(expectedTypeName),
                () -> method.getName() + " 返回类型应包含 " + expectedTypeName + "，实际为 " + method.getGenericReturnType().getTypeName()
        );
    }
}
